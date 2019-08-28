package com.tom.logisticsbridge.pipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import com.tom.logisticsbridge.api.BridgeStack;
import com.tom.logisticsbridge.tileentity.TileEntityBridge;

import logisticspipes.interfaces.IChangeListener;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.interfaces.routing.IRequireReliableTransport;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.IPromise;
import logisticspipes.request.ItemCraftingTemplate;
import logisticspipes.request.RequestLog;
import logisticspipes.request.RequestTree;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LinkedLogisticsOrderList;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsItemOrderManager;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import network.rs485.logisticspipes.connection.NeighborTileEntity;
import network.rs485.logisticspipes.world.WorldCoordinatesWrapper;

public class BridgePipe extends CoreRoutedPipe implements IProvideItems, IRequestItems, IChangeListener, ICraftItems, IRequireReliableTransport {
	public static TextureType TEXTURE = Textures.empty;
	//protected LogisticsItemOrderManager _orderManager = new LogisticsItemOrderManager(this, this);
	private TileEntityBridge bridge;
	private EnumFacing dir;
	private Req reqapi = new Req();
	public BridgePipe(Item item) {
		super(item);
		_orderItemManager = new LogisticsItemOrderManager(this, this);
	}

	@Override
	public ItemSendMode getItemSendMode() {
		return ItemSendMode.Normal;
	}

	@Override
	public TextureType getCenterTexture() {
		return TEXTURE;
	}

	@Override
	public LogisticsModule getLogisticsModule() {
		return null;
	}

	@Override
	public void canProvide(RequestTreeNode tree, RequestTree root, List<IFilter> filters) {
		/*System.out.println("BridgePipe.canProvide()");
		System.out.println(tree);
		System.out.println(root);*/
		if (!isEnabled()) {
			return;
		}
		if (tree.getRequestType() instanceof ItemResource) {
			ItemIdentifier item = ((ItemResource) tree.getRequestType()).getItem();
			for (IFilter filter : filters) {
				if (filter.isBlocked() == filter.isFilteredItem(item.getUndamaged()) || filter.blockProvider()) {
					return;
				}
			}

			// Check the transaction and see if we have helped already
			int canProvide = getAvailableItemCount(item);
			canProvide -= root.getAllPromissesFor(this, item);
			if (canProvide < 1) {
				return;
			}
			LogisticsPromise promise = new LogisticsPromise(item, Math.min(canProvide, tree.getMissingAmount()), this, ResourceType.PROVIDER);
			tree.addPromise(promise);
		} else if (tree.getRequestType() instanceof DictResource) {
			DictResource dict = (DictResource) tree.getRequestType();
			HashMap<ItemIdentifier, Integer> available = new HashMap<>();
			getAllItems(available, filters);
			for (Entry<ItemIdentifier, Integer> item : available.entrySet()) {
				if (!dict.matches(item.getKey(), IResource.MatchSettings.NORMAL)) {
					continue;
				}
				int canProvide = getAvailableItemCount(item.getKey());
				canProvide -= root.getAllPromissesFor(this, item.getKey());
				if (canProvide < 1) {
					continue;
				}
				LogisticsPromise promise = new LogisticsPromise(item.getKey(), Math.min(canProvide, tree.getMissingAmount()), this, ResourceType.PROVIDER);
				tree.addPromise(promise);
				if (tree.getMissingAmount() <= 0) {
					break;
				}
			}
		}
	}

	private int getAvailableItemCount(ItemIdentifier item) {
		if (!isEnabled()) {
			return 0;
		}
		return itemCount(item) - _orderItemManager.totalItemsCountInOrders(item);
	}
	//bridge.craftStack(type.getAsItem().makeNormalStack(1), type.getRequestedAmount(), false);
	@Override
	public LogisticsOrder fullFill(LogisticsPromise promise, IRequestItems destination, IAdditionalTargetInformation info) {
		if(destination == this || bridge == null)return null;
		/*System.out.println("BridgePipe.fullFill()");
		System.out.println(promise);*/
		spawnParticle(Particles.WhiteParticle, 2);
		long count = bridge.countItem(promise.item.makeNormalStack(1), true);
		if(count < promise.numberOfItems){
			bridge.craftStack(promise.item.makeNormalStack(1), (int) (promise.numberOfItems - count), false);
			return _orderItemManager.addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.CRAFTING, info);
		}
		return _orderItemManager.addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination, ResourceType.PROVIDER, info);
	}
	@Override
	public void enabledUpdateEntity() {
		super.enabledUpdateEntity();

		if(isNthTick(10)){
			WorldCoordinatesWrapper w = new WorldCoordinatesWrapper(container);
			NeighborTileEntity<TileEntity> ate = null;
			for(EnumFacing f : EnumFacing.HORIZONTALS){
				NeighborTileEntity<TileEntity> n = w.getNeighbor(f);
				if(n != null && n.getTileEntity() instanceof TileEntityBridge)ate = n;
			}
			if(ate == null){
				dir = null;
				bridge = null;
			}else{
				dir = ate.getDirection();
				bridge = (TileEntityBridge) ate.getTileEntity();
				bridge.reqapi = reqapi;
			}
		}

		if (!_orderItemManager.hasOrders(ResourceType.PROVIDER, ResourceType.CRAFTING) || getWorld().getTotalWorldTime() % 6 != 0) {
			return;
		}

		int itemsleft = itemsToExtract();
		int stacksleft = stacksToExtract();
		LogisticsItemOrder firstOrder = null;
		LogisticsItemOrder order = null;
		while (itemsleft > 0 && stacksleft > 0 && _orderItemManager.hasOrders(ResourceType.CRAFTING) && (firstOrder == null || firstOrder != order)) {
			if (firstOrder == null) {
				firstOrder = order;
			}
			order = _orderItemManager.peekAtTopRequest(ResourceType.CRAFTING);
			if(order == null)break;
			int sent = sendStack(order.getResource().stack, itemsleft, order.getRouter().getSimpleID(), order.getInformation(), false);
			spawnParticle(Particles.VioletParticle, 2);
			if (sent < 0) {
				continue;
			}
			stacksleft -= 1;
			itemsleft -= sent;
		}
		while (itemsleft > 0 && stacksleft > 0 && _orderItemManager.hasOrders(ResourceType.PROVIDER) && (firstOrder == null || firstOrder != order)) {
			if (firstOrder == null) {
				firstOrder = order;
			}
			order = _orderItemManager.peekAtTopRequest(ResourceType.PROVIDER);
			if(order == null)break;
			int sent = sendStack(order.getResource().stack, itemsleft, order.getRouter().getSimpleID(), order.getInformation(), true);
			if (sent < 0) {
				break;
			}
			spawnParticle(Particles.VioletParticle, 3);
			stacksleft -= 1;
			itemsleft -= sent;
		}
	}
	protected int neededEnergy() {
		return 1;
	}

	protected int itemsToExtract() {
		return 8;
	}

	protected int stacksToExtract() {
		return 1;
	}
	private int sendStack(ItemIdentifierStack stack, int maxCount, int destination, IAdditionalTargetInformation info, boolean setFailed) {
		ItemIdentifier item = stack.getItem();

		int available = this.itemCount(item);
		if (available == 0) {
			if(setFailed)_orderItemManager.sendFailed();
			else _orderItemManager.deferSend();
			return 0;
		}

		int wanted = Math.min(available, stack.getStackSize());
		wanted = Math.min(wanted, maxCount);
		wanted = Math.min(wanted, item.getMaxStackSize());
		IRouter dRtr = SimpleServiceLocator.routerManager.getRouterUnsafe(destination, false);
		if (dRtr == null) {
			_orderItemManager.sendFailed();
			return 0;
		}
		SinkReply reply = LogisticsManager.canSink(dRtr, null, true, stack.getItem(), null, true, false);
		boolean defersend = false;
		if (reply != null) {// some pipes are not aware of the space in the adjacent inventory, so they return null
			if (reply.maxNumberOfItems < wanted) {
				wanted = reply.maxNumberOfItems;
				if (wanted <= 0) {
					_orderItemManager.deferSend();
					return 0;
				}
				defersend = true;
			}
		}
		if (!canUseEnergy(wanted * neededEnergy())) {
			return -1;
		}
		ItemStack removed = this.getMultipleItems(item, wanted);
		if (removed == null || removed.getCount() == 0) {
			if(setFailed)_orderItemManager.sendFailed();
			else _orderItemManager.deferSend();
			return 0;
		}
		int sent = removed.getCount();
		useEnergy(sent * neededEnergy());

		IRoutedItem routedItem = SimpleServiceLocator.routedItemHelper.createNewTravelItem(removed);
		routedItem.setDestination(destination);
		routedItem.setTransportMode(TransportMode.Active);
		routedItem.setAdditionalTargetInformation(info);
		super.queueRoutedItem(routedItem, dir != null ? dir : EnumFacing.UP);

		_orderItemManager.sendSuccessfull(sent, defersend, routedItem);
		return sent;
	}
	@Override
	public void onAllowedRemoval() {
		while (_orderItemManager.hasOrders(ResourceType.PROVIDER)) {
			_orderItemManager.sendFailed();
		}
	}
	/**
	 * @param list <item, amount>
	 * */
	@Override
	public void getAllItems(Map<ItemIdentifier, Integer> items, List<IFilter> filters) {
		if (!isEnabled()) {
			return;
		}
		//System.out.println("BridgePipe.getAllItems()");
		HashMap<ItemIdentifier, Integer> addedItems = new HashMap<>();
		getItemsAndCount().entrySet().stream().filter(i -> {
			for (IFilter filter : filters) {
				if (filter.isBlocked() == filter.isFilteredItem(i.getKey().getUndamaged()) || filter.blockProvider()) {
					return false;
				}
			}
			return true;
		}).forEach(next -> addedItems.merge(next.getKey(), next.getValue(), (a, b) -> a + b));
		for (Entry<ItemIdentifier, Integer> item : addedItems.entrySet()) {
			int remaining = item.getValue() - _orderItemManager.totalItemsCountInOrders(item.getKey());
			if (remaining < 1) {
				continue;
			}

			items.put(item.getKey(), remaining);
		}
	}

	/*@Override
	public List<ItemStack> getProvidedItems() {
		System.out.println("BridgePipe.getProvidedItems()");
		return null;
	}

	@Override
	public List<ItemStack> getCraftedItems() {
		System.out.println("BridgePipe.getCraftedItems()");
		return null;
	}

	@Override
	public SimulationResult simulateRequest(ItemStack wanted) {
		System.out.println("BridgePipe.simulateRequest()");
		System.out.println(wanted);
		return null;
	}

	@Override
	public List<ItemStack> performRequest(ItemStack wanted) {
		System.out.println("BridgePipe.performRequest()");
		System.out.println(wanted);
		return null;
	}*/
	@Override
	public boolean hasGenericInterests() {
		return true;
	}

	public int itemCount(ItemIdentifier item) {
		if(bridge == null)return 0;
		return (int) bridge.countItem(item.makeNormalStack(1), true);
	}

	public Map<ItemIdentifier, Integer> getItemsAndCount() {
		if(bridge == null){
			return Collections.emptyMap();
		}
		List<ItemStack> prov = reqapi.getProvidedItems();
		return bridge.getItems().stream().filter(st -> !prov.stream().anyMatch(s -> s.isItemEqual(st.obj) && ItemStack.areItemStackTagsEqual(s, st.obj))).
				map(s -> new BridgeStack<>(ItemIdentifier.get(s.obj), s.size + s.requestableSize, s.craftable, 0)).
				collect(Collectors.toMap(s -> s.obj, s -> (int)s.size));
	}

	public ItemStack getMultipleItems(ItemIdentifier item, int count) {
		if(bridge == null)return ItemStack.EMPTY;
		return bridge.extractStack(item.makeNormalStack(1), count, false);
	}

	@Override public void listenedChanged() {}

	@Override
	public boolean logisitcsIsPipeConnected(TileEntity tile, EnumFacing dir) {
		return tile instanceof TileEntityBridge;
	}

	@Override
	public void registerExtras(IPromise promise) {
		/*System.out.println("BridgePipe.registerExtras()");
		System.out.println(promise);*/
	}

	@Override
	public ICraftingTemplate addCrafting(IResource type) {
		if(bridge == null)return null;
		ItemStack item = type.getAsItem().makeNormalStack(1);
		List<ItemStack> cr = reqapi.getCraftedItems();
		if(!bridge.getItems().stream().anyMatch(b -> b.craftable && item.isItemEqual(b.obj) && !cr.stream().anyMatch(i -> item.isItemEqual(i))))return null;
		/*Map<ItemStack, int[]> res = bridge.craftStack(type.getAsItem().makeNormalStack(1), type.getRequestedAmount(), true);
		if(res == null)return null;*/
		//System.out.println(res);
		return new ItemCraftingTemplate(type.getAsItem().makeStack(type.getRequestedAmount()), this, 0);
	}

	@Override
	public boolean canCraft(IResource toCraft) {
		/*System.out.println("BridgePipe.canCraft()");
		System.out.println(toCraft);*/
		return false;
	}

	@Override
	public int getTodo() {
		return 0;
	}

	@Override
	public void itemLost(ItemIdentifierStack item, IAdditionalTargetInformation info) {

	}

	@Override
	public void itemArrived(ItemIdentifierStack item, IAdditionalTargetInformation info) {

	}

	@Override
	public List<ItemIdentifierStack> getCraftedItems() {
		if(bridge == null){
			return Collections.emptyList();
		}
		return bridge.getItems().stream().filter(b -> b.craftable).
				map(s -> new ItemIdentifierStack(ItemIdentifier.get(s.obj), 1)).
				collect(Collectors.toList());
	}
	public class Req {
		private boolean gettingProvItems;
		public List<ItemStack> getProvidedItems() {
			if(gettingProvItems || !isEnabled())return Collections.emptyList();
			if (stillNeedReplace()) {
				return new ArrayList<>();
			}
			try {
				gettingProvItems = true;
				IRouter myRouter = getRouter();
				List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
				exits.removeIf(e -> e.destination == myRouter);
				Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
				List<ItemStack> list = new ArrayList<>(items.size());
				for (Entry<ItemIdentifier, Integer> item : items.entrySet()) {
					ItemStack is = item.getKey().unsafeMakeNormalStack(item.getValue());
					list.add(is);
				}
				return list;
			} finally {
				gettingProvItems = false;
			}
		}

		public List<ItemStack> getCraftedItems() {
			if(!isEnabled())return Collections.emptyList();
			if (stillNeedReplace()) {
				return new ArrayList<>();
			}
			IRouter myRouter = getRouter();
			List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
			exits.removeIf(e -> e.destination == myRouter);
			LinkedList<ItemIdentifier> items = SimpleServiceLocator.logisticsManager.getCraftableItems(exits);
			List<ItemStack> list = new ArrayList<>(items.size());
			for (ItemIdentifier item : items) {
				ItemStack is = item.unsafeMakeNormalStack(1);
				list.add(is);
			}
			return list;
		}

		public OpResult simulateRequest(ItemStack wanted, boolean craft, boolean allowPartial) {
			if(!isEnabled()){
				OpResult r = new OpResult();
				r.used = Collections.emptyList();
				r.missing = Collections.singletonList(wanted);
				return r;
			}
			IRouter myRouter = getRouter();
			List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
			exits.removeIf(e -> e.destination == myRouter);
			Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
			ItemIdentifier req = ItemIdentifier.get(wanted);
			int count = items.getOrDefault(req, 0);
			if(count == 0 && !craft){
				OpResult r = new OpResult();
				r.used = Collections.emptyList();
				r.missing = Collections.singletonList(wanted);
				return r;
			}
			ListLog ll = new ListLog();
			/*System.out.println("BridgePipe.Req.simulateRequest()");
			System.out.println(wanted);*/
			simulate(ItemIdentifier.get(wanted).makeStack(craft ? wanted.getCount() : Math.min(count, wanted.getCount())), BridgePipe.this, ll);
			OpResult res = ll.asResult();
			if(!craft && !allowPartial && wanted.getCount() > count){
				int missingCount = wanted.getCount() - count;
				ItemStack missingStack = wanted.copy();
				missingStack.setCount(missingCount);
				res.missing.add(missingStack);
			}

			return res;
		}

		public OpResult performRequest(ItemStack wanted, boolean craft) {
			if(!isEnabled())return new OpResult(Collections.singletonList(wanted));
			ListLog ll = new ListLog();
			IRouter myRouter = getRouter();
			List<ExitRoute> exits = new ArrayList<>(myRouter.getIRoutersByCost());
			exits.removeIf(e -> e.destination == myRouter);
			Map<ItemIdentifier, Integer> items = SimpleServiceLocator.logisticsManager.getAvailableItems(exits);
			ItemIdentifier req = ItemIdentifier.get(wanted);
			int count = items.getOrDefault(req, 0);
			if(count == 0 && !craft){
				return new OpResult(Collections.singletonList(wanted));
			}
			//System.out.println("BridgePipe.Req.performRequest()");
			int reqCount = craft ? wanted.getCount() : Math.min(count, wanted.getCount());
			boolean success = request(ItemIdentifier.get(wanted).makeStack(reqCount), BridgePipe.this, ll, null);
			OpResult res = ll.asResult();
			if(!craft && wanted.getCount() > count){
				int missingCount = wanted.getCount() - count;
				ItemStack missingStack = wanted.copy();
				missingStack.setCount(missingCount);
				res.missing.add(missingStack);
			}
			/*if(success){
				ItemStack w = wanted.copy();
				w.setCount(reqCount);
				res.used.add(w);
			}*/

			return res;
		}
	}
	public static class OpResult {
		public List<ItemStack> used;
		public List<ItemStack> missing;
		public OpResult() {
		}
		public OpResult(List<ItemStack> missing) {
			this.missing = missing;
			used = Collections.emptyList();
		}
		public OpResult(List<ItemStack> used, List<ItemStack> missing) {
			this.used = used;
			this.missing = missing;
		}
	}
	public static boolean request(ItemIdentifierStack item, IRequestItems requester, RequestLog log, IAdditionalTargetInformation info) {
		return RequestTree.request(item, requester, log, false, false, true, true, RequestTree.defaultRequestFlags, info) == item.getStackSize();
	}

	public static int simulate(ItemIdentifierStack item, IRequestItems requester, RequestLog log) {
		return RequestTree.request(item, requester, log, true, true, false, true, RequestTree.defaultRequestFlags, null);
	}
	public static class ListLog implements RequestLog {
		private final List<IResource> missing = new ArrayList<>();
		private final List<IResource> used = new ArrayList<>();
		@Override
		public void handleMissingItems(List<IResource> resources) {
			missing.addAll(resources);
		}

		@Override
		public void handleSucessfullRequestOf(IResource item, LinkedLogisticsOrderList paticipating) {
		}

		@Override
		public void handleSucessfullRequestOfList(List<IResource> resources, LinkedLogisticsOrderList paticipating) {
			used.addAll(resources);
		}
		public List<ItemStack> getMissingItems(){
			return toList(missing);
		}
		public List<ItemStack> getUsedItems(){
			return toList(used);
		}
		public OpResult asResult(){
			return new OpResult(getUsedItems(), getMissingItems());
		}
		private static List<ItemStack> toList(List<IResource> resList){
			List<ItemStack> list = new ArrayList<>(resList.size());
			for (IResource e : resList) {
				if (e instanceof ItemResource) {
					list.add(((ItemResource) e).getItem().unsafeMakeNormalStack(e.getRequestedAmount()));
				} else if (e instanceof DictResource) {
					list.add(((DictResource) e).getItem().unsafeMakeNormalStack(e.getRequestedAmount()));
				}
			}
			return list;
		}
	}
}