package com.feed_the_beast.mods.ftbchunks.client;

import com.feed_the_beast.mods.ftbchunks.client.map.MapDimension;
import com.feed_the_beast.mods.ftbchunks.client.map.MapRegion;
import com.feed_the_beast.mods.ftbchunks.client.map.MapRegionData;
import com.feed_the_beast.mods.ftbchunks.client.map.Waypoint;
import com.feed_the_beast.mods.ftbchunks.impl.XZ;
import com.feed_the_beast.mods.ftbchunks.net.FTBChunksNet;
import com.feed_the_beast.mods.ftbchunks.net.RequestPlayerListPacket;
import com.feed_the_beast.mods.ftbchunks.net.TeleportFromMapPacket;
import com.feed_the_beast.mods.ftbguilibrary.config.ConfigString;
import com.feed_the_beast.mods.ftbguilibrary.config.gui.GuiEditConfigFromString;
import com.feed_the_beast.mods.ftbguilibrary.icon.Color4I;
import com.feed_the_beast.mods.ftbguilibrary.utils.Key;
import com.feed_the_beast.mods.ftbguilibrary.utils.MathUtils;
import com.feed_the_beast.mods.ftbguilibrary.utils.MouseButton;
import com.feed_the_beast.mods.ftbguilibrary.utils.StringUtils;
import com.feed_the_beast.mods.ftbguilibrary.widget.Button;
import com.feed_the_beast.mods.ftbguilibrary.widget.ContextMenuItem;
import com.feed_the_beast.mods.ftbguilibrary.widget.GuiBase;
import com.feed_the_beast.mods.ftbguilibrary.widget.GuiIcons;
import com.feed_the_beast.mods.ftbguilibrary.widget.SimpleButton;
import com.feed_the_beast.mods.ftbguilibrary.widget.Theme;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LatvianModder
 */
public class LargeMapScreen extends GuiBase
{
	public Color4I backgroundColor = Color4I.rgb(0x202225);

	public RegionMapPanel regionPanel;
	public int zoom = 256;
	public MapDimension dimension;
	public int scrollWidth = 0;
	public int scrollHeight = 0;
	public int prevMouseX, prevMouseY;
	public int grabbed = 0;
	public boolean movedToPlayer = false;
	public Button claimChunksButton, dimensionButton, waypointsButton, settingsButton, alliesButton, syncButton;

	public LargeMapScreen()
	{
		regionPanel = new RegionMapPanel(this);
		dimension = MapDimension.getCurrent();
		regionPanel.setScrollX(0D);
		regionPanel.setScrollY(0D);
	}

	public int getRegionButtonSize()
	{
		return zoom * 2;
	}

	public void addZoom(double up)
	{
		int z = zoom;

		if (up > 0D)
		{
			zoom *= 2;
		}
		else
		{
			zoom /= 2;
		}

		zoom = MathHelper.clamp(zoom, 1, 1024);

		if (zoom != z)
		{
			grabbed = 0;
			double sx = regionPanel.regionX;
			double sy = regionPanel.regionZ;
			regionPanel.resetScroll();
			regionPanel.scrollTo(sx, sy);

			ObfuscationReflectionHelper.setPrivateValue(MouseHelper.class, Minecraft.getInstance().mouseHelper, true, "field_198051_p");
			Minecraft.getInstance().mouseHelper.ungrabMouse();
		}
	}

	@Override
	public void addWidgets()
	{
		add(regionPanel);

		add(claimChunksButton = new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.claimed_chunks"), GuiIcons.MAP, (b, m) -> new ChunkScreen().openGui()));
		/*
		add(waypointsButton = new SimpleButton(this, I18n.format("ftbchunks.gui.waypoints"), GuiIcons.BEACON, (b, m) -> {
			Minecraft.getInstance().getToastGui().add(new SystemToast(SystemToast.Type.TUTORIAL_HINT, new StringTextComponent("WIP!"), null));
		}));
		 */

		add(alliesButton = new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.allies"), GuiIcons.FRIENDS, (b, m) -> FTBChunksNet.MAIN.sendToServer(new RequestPlayerListPacket())));
		add(waypointsButton = new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.add_waypoint"), GuiIcons.ADD, (b, m) -> {
			ConfigString name = new ConfigString();
			new GuiEditConfigFromString<>(name, set -> {
				if (set)
				{
					PlayerEntity player = Minecraft.getInstance().player;
					Waypoint w = new Waypoint(dimension);
					w.name = name.value;
					w.color = Color4I.hsb(MathUtils.RAND.nextFloat(), 1F, 1F).rgba();
					w.x = MathHelper.floor(player.getPosX());
					w.z = MathHelper.floor(player.getPosZ());
					dimension.getWaypoints().add(w);
					dimension.saveData = true;
					refreshWidgets();
				}

				openGui();
			}).openGui();
		}));

		add(syncButton = new SimpleButton(this, /*new TranslationTextComponent("ftbchunks.gui.sync")*/new StringTextComponent("Currently disabled due to a bug!"), GuiIcons.REFRESH, (b, m) -> {
			// dimension.sync();
		}));

		add(dimensionButton = new SimpleButton(this, new StringTextComponent(dimension.dimension.getLocation().getPath().replace('_', ' ')), GuiIcons.GLOBE, (b, m) -> {
			try
			{
				List<MapDimension> list = new ArrayList<>(dimension.manager.getDimensions().values());
				int i = list.indexOf(dimension);

				if (i != -1)
				{
					dimension = list.get(MathUtils.mod(i + (m.isLeft() ? 1 : -1), list.size()));
					refreshWidgets();
					movedToPlayer = false;
				}
			}
			catch (Exception ex)
			{
			}
		}));

		add(settingsButton = new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.settings"), GuiIcons.SETTINGS, (b, m) -> FTBChunksClientConfig.openSettings()));
	}

	@Override
	public void alignWidgets()
	{
		claimChunksButton.setPosAndSize(1, 1, 16, 16);
		alliesButton.setPosAndSize(1, 19, 16, 16);
		waypointsButton.setPosAndSize(1, 37, 16, 16);
		syncButton.setPosAndSize(1, 55, 16, 16);
		dimensionButton.setPosAndSize(1, height - 36, 16, 16);
		settingsButton.setPosAndSize(1, height - 18, 16, 16);
	}

	@Override
	public boolean onInit()
	{
		return setFullscreen();
	}

	@Override
	public boolean mousePressed(MouseButton button)
	{
		if (super.mousePressed(button))
		{
			return true;
		}

		if (button.isLeft())
		{
			prevMouseX = getMouseX();
			prevMouseY = getMouseY();
			return true;
		}
		else if (button.isRight())
		{
			int clickBlockX = regionPanel.blockX;
			int clickBlockZ = regionPanel.blockZ;
			List<ContextMenuItem> list = new ArrayList<>();
			list.add(new ContextMenuItem(new TranslationTextComponent("ftbchunks.gui.add_waypoint"), GuiIcons.ADD, () -> {
				ConfigString name = new ConfigString();
				new GuiEditConfigFromString<>(name, set -> {
					if (set)
					{
						Waypoint w = new Waypoint(dimension);
						w.name = name.value;
						w.color = Color4I.hsb(MathUtils.RAND.nextFloat(), 1F, 1F).rgba();
						w.x = clickBlockX;
						w.z = clickBlockZ;
						dimension.getWaypoints().add(w);
						dimension.saveData = true;
						refreshWidgets();
					}

					openGui();
				}).openGui();
			}));
			openContextMenu(list);
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(Key key)
	{
		if (key.is(GLFW.GLFW_KEY_T))
		{
			FTBChunksNet.MAIN.sendToServer(new TeleportFromMapPacket(regionPanel.blockX, regionPanel.blockZ, dimension.dimension));
			closeGui(false);
			return true;
		}
		else if (key.is(FTBChunksClient.openMapKey.getKey().getKeyCode()))
		{
			closeGui(true);
			return true;
		}
		else if (key.is(GLFW.GLFW_KEY_W))
		{
			return true;
		}
		else if (key.is(GLFW.GLFW_KEY_SPACE))
		{
			movedToPlayer = false;
			return true;
		}

		return super.keyPressed(key);
	}

	@Override
	public boolean drawDefaultBackground(MatrixStack matrixStack)
	{
		if (!movedToPlayer)
		{
			PlayerEntity p = Minecraft.getInstance().player;
			regionPanel.resetScroll();
			regionPanel.scrollTo(p.getPosX() / 512D, p.getPosZ() / 512D);
			movedToPlayer = true;
		}

		backgroundColor.draw(matrixStack, 0, 0, width, height);
		return false;
	}

	@Override
	public void drawBackground(MatrixStack matrixStack, Theme theme, int x, int y, int w, int h)
	{
		if (grabbed != 0)
		{
			int mx = getMouseX();
			int my = getMouseY();

			if (scrollWidth > regionPanel.width)
			{
				regionPanel.setScrollX(Math.max(Math.min(regionPanel.getScrollX() + (prevMouseX - mx), scrollWidth - regionPanel.width), 0));
			}

			if (scrollHeight > regionPanel.height)
			{
				regionPanel.setScrollY(Math.max(Math.min(regionPanel.getScrollY() + (prevMouseY - my), scrollHeight - regionPanel.height), 0));
			}

			prevMouseX = mx;
			prevMouseY = my;
		}

		if (scrollWidth <= regionPanel.width)
		{
			regionPanel.setScrollX((scrollWidth - regionPanel.width) / 2D);
		}

		if (scrollHeight <= regionPanel.height)
		{
			regionPanel.setScrollY((scrollHeight - regionPanel.height) / 2D);
		}

		GlStateManager.disableTexture();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		int r = 70;
		int g = 70;
		int b = 70;
		int a = 100;

		buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

		int s = getRegionButtonSize();
		double ox = -regionPanel.getScrollX() % s;
		double oy = -regionPanel.getScrollY() % s;

		for (int gx = 0; gx <= (w / s) + 1; gx++)
		{
			buffer.pos(x + ox + gx * s, y, 0).color(r, g, b, a).endVertex();
			buffer.pos(x + ox + gx * s, y + h, 0).color(r, g, b, a).endVertex();
		}

		for (int gy = 0; gy <= (h / s) + 1; gy++)
		{
			buffer.pos(x, y + oy + gy * s, 0).color(r, g, b, a).endVertex();
			buffer.pos(x + w, y + oy + gy * s, 0).color(r, g, b, a).endVertex();
		}

		tessellator.draw();
		GlStateManager.enableTexture();
	}

	@Override
	public void drawForeground(MatrixStack matrixStack, Theme theme, int x, int y, int w, int h)
	{
		String coords = "X: " + regionPanel.blockX + ", Y: " + (regionPanel.blockY == 0 ? "??" : regionPanel.blockY) + ", Z: " + regionPanel.blockZ;

		if (regionPanel.blockY != 0)
		{
			MapRegion region = dimension.getRegion(XZ.regionFromBlock(regionPanel.blockX, regionPanel.blockZ));
			MapRegionData data = region.getData();

			if (data != null)
			{
				int waterLightAndBiome = data.waterLightAndBiome[regionPanel.blockX & 511 + (regionPanel.blockZ & 511) * 512];
				RegistryKey<Biome> biome = dimension.manager.getBiomeKey(waterLightAndBiome);
				Block block = dimension.manager.getBlock(data.getBlockIndex(regionPanel.blockX & 511 + (regionPanel.blockZ & 511) * 512));
				coords = coords + " | " + I18n.format("biome." + biome.getLocation().getNamespace() + "." + biome.getLocation().getPath()) + " | " + I18n.format(block.getTranslationKey());

				if ((waterLightAndBiome & (1 << 15)) != 0)
				{
					coords += " (in water)";
				}
			}
		}

		int coordsw = theme.getStringWidth(coords) / 2;

		backgroundColor.withAlpha(150).draw(matrixStack, x + (w - coordsw) / 2, y + h - 6, coordsw + 4, 6);
		matrixStack.push();
		matrixStack.translate(x + (w - coordsw) / 2F + 2F, y + h - 5, 0F);
		matrixStack.scale(0.5F, 0.5F, 1F);
		theme.drawString(matrixStack, coords, 0, 0, Theme.SHADOW);
		matrixStack.pop();

		if (FTBChunksClientConfig.debugInfo)
		{
			long memory = 0L;

			for (MapDimension dim : dimension.manager.getDimensions().values())
			{
				for (MapRegion region : dim.getLoadedRegions())
				{
					if (region.data != null)
					{
						memory += 2L * 2L * 512L * 512L; // height, waterLightAndBiome
						memory += 3L * 4L * 512L * 512L; // foliage, grass, water
					}
				}
			}

			String memoryUsage = "Estimated Memory Usage: " + StringUtils.formatDouble00(memory / 1024D / 1024D) + " MB";
			int memoryUsagew = theme.getStringWidth(memoryUsage) / 2;

			backgroundColor.withAlpha(150).draw(matrixStack, x + (w - memoryUsagew) - 2, y, memoryUsagew + 4, 6);

			matrixStack.push();
			matrixStack.translate(x + (w - memoryUsagew) - 1F, y + 1, 0F);
			matrixStack.scale(0.5F, 0.5F, 1F);
			theme.drawString(matrixStack, memoryUsage, 0, 0, Theme.SHADOW);
			matrixStack.pop();
		}
	}
}