package com.tom.logisticsbridge.gui;

import java.io.IOException;

import network.rs485.logisticspipes.util.TextUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.inventory.ContainerCraftingManagerU;
import com.tom.logisticsbridge.pipe.CraftingManager.BlockingMode;
import com.tom.logisticsbridge.tileentity.ICraftingManager;

import logisticspipes.utils.gui.LogisticsBaseGuiScreen;
import logisticspipes.utils.gui.SmallGuiButton;
import logisticspipes.utils.gui.extention.GuiExtention;

public class GuiCraftingManagerU extends LogisticsBaseGuiScreen {
	private static final ResourceLocation BG = new ResourceLocation(LogisticsBridge.ID, "textures/gui/crafting_manager.png");
	private EntityPlayer player;
	private ICraftingManager pipe;
	public GuiCraftingManagerU(EntityPlayer player, ICraftingManager pipe) {
		super(new ContainerCraftingManagerU(player, pipe));
		this.player = player;
		this.pipe = pipe;
		ySize++;
	}
	public void bindTexture(ResourceLocation loc){
		mc.getTextureManager().bindTexture(loc);
	}
	@Override
	protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		bindTexture(BG);
		this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
		super.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
	}
	/**
	 * Draw the foreground layer for the GuiContainer (everything in front of the items)
	 */
	@Override
	protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		super.drawGuiContainerForegroundLayer(mouseX, mouseY);
		this.fontRenderer.drawString(I18n.format("gui.craftingManager"), 8, 6, 4210752);
		this.fontRenderer.drawString(this.player.inventory.getDisplayName().getUnformattedText(), 8, this.ySize - 96 + 2, 4210752);
	}

	@Override
	public void initGui() {
		super.initGui();//120 155
		extentionControllerLeft.clear();
		ConfigExtention ce = new ConfigExtention(TextUtil.translate("gui.craftingManager.satellite"), pipe.satelliteDisplayStack(), 0);
		ce.registerButton(extentionControllerLeft.registerControlledButton(addButton(new SmallGuiButton(0, guiLeft - 45, guiTop + 25, 40, 10, TextUtil.translate("gui.crafting.Select")))));
		extentionControllerLeft.addExtention(ce);
		ce = new ConfigExtention(TextUtil.translate("gui.craftingManager.blocking"), new ItemStack(Blocks.BARRIER), 2) {

			@Override
			public String getString() {
				return TextUtil.translate("gui.craftingManager.blocking." + pipe.getBlockingMode().name().toLowerCase());
			}

			@Override
			public int textOff() {
				return 70;
			}

			@Override
			public int getFinalWidth() {
				return 140;
			}
		};
		ce.registerButton(extentionControllerLeft.registerControlledButton(addButton(new SmallGuiButton(1, guiLeft - 45, guiTop + 11, 40, 10, TextUtil.translate("gui.craftingManager.blocking.change")))));
		extentionControllerLeft.addExtention(ce);
	}
	@Override
	protected void actionPerformed(GuiButton button) throws IOException {
		switch (button.id) {
		case 0:
			openSubGuiForSatelliteSelection(0);
			return;

		case 1:
			BlockingMode m = BlockingMode.VALUES[(pipe.getBlockingMode().ordinal() + 1) % BlockingMode.VALUES.length];
			if(m == BlockingMode.NULL)m = BlockingMode.OFF;
			pipe.setPipeID(1, Integer.toString(m.ordinal()), null);
			return;
		}
	}
	private void openSubGuiForSatelliteSelection(int id) {
		this.setSubGui(new GuiSelectIDPopup(pipe.getPosition(), id, 0, uuid -> pipe.setPipeID(id, uuid, null)));
	}
	public RenderItem renderItem() {
		return itemRender;
	}
	public FontRenderer fontRenderer() {
		return fontRenderer;
	}
	public class ConfigExtention extends GuiExtention {
		private final String name;
		private final ItemStack stack;
		private final int id;
		public ConfigExtention(String name, ItemStack stack, int id) {
			this.name = name;
			this.stack = stack;
			this.id = id;
		}
		@Override
		public int getFinalWidth() {
			return 120;
		}

		@Override
		public int getFinalHeight() {
			return 40;
		}

		@Override
		public void renderForground(int left, int top) {
			String pid = getString();
			if (!isFullyExtended()) {
				GL11.glEnable(GL12.GL_RESCALE_NORMAL);
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240 / 1.0F, 240 / 1.0F);
				GL11.glEnable(GL11.GL_LIGHTING);
				GL11.glEnable(GL11.GL_DEPTH_TEST);
				RenderHelper.enableGUIStandardItemLighting();
				renderItem().renderItemAndEffectIntoGUI(stack, left + 5, top + 5);
				renderItem().renderItemOverlayIntoGUI(fontRenderer(), stack, left + 5, top + 5, "");
				GL11.glDisable(GL11.GL_LIGHTING);
				GL11.glDisable(GL11.GL_DEPTH_TEST);
				renderItem().zLevel = 0.0F;
			} else {
				mc.fontRenderer.drawString(name, left + 9, top + 8, 0x404040);
				if (pid == null || pid.isEmpty()) {
					mc.fontRenderer.drawString(TextUtil.translate("gui.craftingManager.noConnection"), left + 40, top + 22, 0x404040);
				} else {
					mc.fontRenderer.drawString("" + pid, left + textOff() - mc.fontRenderer.getStringWidth("" + pid)/2, top + 22, 0x404040);
				}
			}
		}

		public String getString() {
			return pipe.getPipeID(id);
		}

		public int textOff() {
			return 40;
		}
	}
}
