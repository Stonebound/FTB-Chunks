package com.feed_the_beast.mods.ftbchunks.client;

import com.feed_the_beast.mods.ftbchunks.FTBChunks;
import com.feed_the_beast.mods.ftbchunks.client.map.MapChunk;
import com.feed_the_beast.mods.ftbchunks.client.map.MapDimension;
import com.feed_the_beast.mods.ftbchunks.client.map.MapManager;
import com.feed_the_beast.mods.ftbchunks.client.map.PlayerHeadTexture;
import com.feed_the_beast.mods.ftbchunks.impl.ClaimedChunkManagerImpl;
import com.feed_the_beast.mods.ftbchunks.impl.XZ;
import com.feed_the_beast.mods.ftbchunks.net.FTBChunksNet;
import com.feed_the_beast.mods.ftbchunks.net.RequestChunkChangePacket;
import com.feed_the_beast.mods.ftbchunks.net.RequestMapDataPacket;
import com.feed_the_beast.mods.ftbchunks.net.RequestPlayerListPacket;
import com.feed_the_beast.mods.ftbchunks.net.SendGeneralDataPacket;
import com.feed_the_beast.mods.ftbguilibrary.icon.Color4I;
import com.feed_the_beast.mods.ftbguilibrary.icon.Icon;
import com.feed_the_beast.mods.ftbguilibrary.icon.ImageIcon;
import com.feed_the_beast.mods.ftbguilibrary.utils.Key;
import com.feed_the_beast.mods.ftbguilibrary.utils.MathUtils;
import com.feed_the_beast.mods.ftbguilibrary.utils.MouseButton;
import com.feed_the_beast.mods.ftbguilibrary.utils.TooltipList;
import com.feed_the_beast.mods.ftbguilibrary.widget.Button;
import com.feed_the_beast.mods.ftbguilibrary.widget.GuiBase;
import com.feed_the_beast.mods.ftbguilibrary.widget.GuiHelper;
import com.feed_the_beast.mods.ftbguilibrary.widget.GuiIcons;
import com.feed_the_beast.mods.ftbguilibrary.widget.Panel;
import com.feed_the_beast.mods.ftbguilibrary.widget.SimpleButton;
import com.feed_the_beast.mods.ftbguilibrary.widget.Theme;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.util.UUIDTypeAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.Texture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextProperties;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author LatvianModder
 */
public class ChunkScreen extends GuiBase
{
	public class ChunkButton extends Button
	{
		public final XZ chunkPos;
		public MapChunk chunk;

		public ChunkButton(Panel panel, XZ xz)
		{
			super(panel, StringTextComponent.EMPTY, Icon.EMPTY);
			setSize(FTBChunks.TILE_SIZE, FTBChunks.TILE_SIZE);
			chunkPos = xz;
		}

		@Override
		public void onClicked(MouseButton mouseButton)
		{
			selectedChunks.add(chunkPos);
		}

		@Override
		public void drawBackground(MatrixStack matrixStack, Theme theme, int x, int y, int w, int h)
		{
			if (isMouseOver() || selectedChunks.contains(chunkPos))
			{
				Color4I.WHITE.withAlpha(100).draw(matrixStack, x, y, w, h);

				if (isMouseButtonDown(MouseButton.LEFT) || isMouseButtonDown(MouseButton.RIGHT))
				{
					selectedChunks.add(chunkPos);
				}
			}
		}

		@Override
		@SuppressWarnings("deprecation")
		public void addMouseOverText(TooltipList list)
		{
			if (chunk != null && chunk.owner != StringTextComponent.EMPTY)
			{
				list.add(chunk.owner);

				Date date = new Date();

				if (Screen.hasAltDown())
				{
					list.add(new StringTextComponent(chunk.claimedDate.toLocaleString()).mergeStyle(TextFormatting.GRAY));
				}
				else
				{
					list.add(new StringTextComponent(ClaimedChunkManagerImpl.prettyTimeString((date.getTime() - chunk.claimedDate.getTime()) / 1000L) + " ago").mergeStyle(TextFormatting.GRAY));
				}

				if (chunk.forceLoadedDate != null)
				{
					list.add(new TranslationTextComponent("ftbchunks.gui.force_loaded").mergeStyle(TextFormatting.RED));

					if (Screen.hasAltDown())
					{
						list.add(new StringTextComponent(chunk.forceLoadedDate.toLocaleString()).mergeStyle(TextFormatting.GRAY));
					}
					else
					{
						list.add(new StringTextComponent(ClaimedChunkManagerImpl.prettyTimeString((date.getTime() - chunk.forceLoadedDate.getTime()) / 1000L) + " ago").mergeStyle(TextFormatting.GRAY));
					}
				}
			}
		}
	}

	public MapDimension dimension = MapDimension.getCurrent();
	public List<ChunkButton> chunkButtons;
	public Set<XZ> selectedChunks;

	public ChunkScreen()
	{
		FTBChunksClient.alwaysRenderChunksOnMap = true;

		if (MapManager.inst != null)
		{
			MapManager.inst.updateAllRegions(false);
		}
	}

	@Override
	public boolean onInit()
	{
		return setFullscreen();
	}

	@Override
	public void onClosed()
	{
		FTBChunksClient.alwaysRenderChunksOnMap = false;

		if (MapManager.inst != null)
		{
			MapManager.inst.updateAllRegions(false);
		}

		super.onClosed();
	}

	@Override
	public void addWidgets()
	{
		int sx = getX() + (width - FTBChunks.MINIMAP_SIZE) / 2;
		int sy = getY() + (height - FTBChunks.MINIMAP_SIZE) / 2;
		PlayerEntity player = Minecraft.getInstance().player;
		int startX = player.chunkCoordX - FTBChunks.TILE_OFFSET;
		int startZ = player.chunkCoordZ - FTBChunks.TILE_OFFSET;

		chunkButtons = new ArrayList<>();
		selectedChunks = new LinkedHashSet<>();

		for (int z = 0; z < FTBChunks.TILES; z++)
		{
			for (int x = 0; x < FTBChunks.TILES; x++)
			{
				ChunkButton button = new ChunkButton(this, XZ.of(startX + x, startZ + z));
				button.chunk = dimension.getRegion(XZ.regionFromChunk(startX + x, startZ + z)).getDataBlocking().getChunk(button.chunkPos);
				chunkButtons.add(button);
				button.setPos(sx + x * FTBChunks.TILE_SIZE, sy + z * FTBChunks.TILE_SIZE);
			}
		}

		addAll(chunkButtons);
		FTBChunksNet.MAIN.sendToServer(new RequestMapDataPacket(player.chunkCoordX - FTBChunks.TILE_OFFSET, player.chunkCoordZ - FTBChunks.TILE_OFFSET, player.chunkCoordX + FTBChunks.TILE_OFFSET, player.chunkCoordZ + FTBChunks.TILE_OFFSET));
		add(new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.large_map"), GuiIcons.MAP, (simpleButton, mouseButton) -> new LargeMapScreen().openGui()).setPosAndSize(1, 1, 16, 16));
		add(new SimpleButton(this, new TranslationTextComponent("ftbchunks.gui.allies"), GuiIcons.FRIENDS, (simpleButton, mouseButton) -> FTBChunksNet.MAIN.sendToServer(new RequestPlayerListPacket())).setPosAndSize(1, 19, 16, 16));
	}

	@Override
	public void mouseReleased(MouseButton button)
	{
		super.mouseReleased(button);

		if (!selectedChunks.isEmpty())
		{
			FTBChunksNet.MAIN.sendToServer(new RequestChunkChangePacket(isShiftKeyDown() ? (button.isLeft() ? 2 : 3) : (button.isLeft() ? 0 : 1), selectedChunks));
			selectedChunks.clear();
			playClickSound();
		}
	}

	@Override
	public boolean keyPressed(Key key)
	{
		if (key.is(GLFW.GLFW_KEY_F))
		{
			new LargeMapScreen().openGui();
			return true;
		}

		return super.keyPressed(key);
	}

	@Override
	public void drawBackground(MatrixStack matrixStack, Theme theme, int x, int y, int w, int h)
	{
		TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		PlayerEntity player = Minecraft.getInstance().player;
		int startX = player.chunkCoordX - FTBChunks.TILE_OFFSET;
		int startZ = player.chunkCoordZ - FTBChunks.TILE_OFFSET;

		int sx = x + (w - FTBChunks.MINIMAP_SIZE) / 2;
		int sy = y + (h - FTBChunks.MINIMAP_SIZE) / 2;

		int r = 70;
		int g = 70;
		int b = 70;
		int a = 100;

		RenderSystem.lineWidth(Math.max(2.5F, (float) Minecraft.getInstance().getMainWindow().getFramebufferWidth() / 1920.0F * 2.5F));

		RenderSystem.enableTexture();
		RenderSystem.bindTexture(FTBChunksClient.minimapTextureId);
		RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GuiHelper.drawTexturedRect(matrixStack, sx, sy, FTBChunks.MINIMAP_SIZE, FTBChunks.MINIMAP_SIZE, Color4I.WHITE, 0F, 0F, 1F, 1F);

		if (!InputMappings.isKeyDown(Minecraft.getInstance().getMainWindow().getHandle(), GLFW.GLFW_KEY_TAB))
		{
			GlStateManager.disableTexture();

			buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

			for (int gy = 1; gy < FTBChunks.TILES; gy++)
			{
				buffer.pos(sx, sy + gy * FTBChunks.TILE_SIZE, 0).color(r, g, b, a).endVertex();
				buffer.pos(sx + FTBChunks.MINIMAP_SIZE, sy + gy * FTBChunks.TILE_SIZE, 0).color(r, g, b, a).endVertex();
			}

			for (int gx = 1; gx < FTBChunks.TILES; gx++)
			{
				buffer.pos(sx + gx * FTBChunks.TILE_SIZE, sy, 0).color(r, g, b, a).endVertex();
				buffer.pos(sx + gx * FTBChunks.TILE_SIZE, sy + FTBChunks.MINIMAP_SIZE, 0).color(r, g, b, a).endVertex();
			}

			tessellator.draw();

			buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

			for (ChunkButton button : chunkButtons)
			{
				MapChunk chunk = button.chunk;

				if (chunk.forceLoadedDate == null)
				{
					continue;
				}

				int cx = button.getX();
				int cy = button.getY();

				buffer.pos(cx, cy, 0).color(255, 0, 0, 100).endVertex();
				buffer.pos(cx + FTBChunks.TILE_SIZE, cy + FTBChunks.TILE_SIZE, 0).color(255, 0, 0, 100).endVertex();

				buffer.pos(cx + FTBChunks.TILE_SIZE / 2F, cy, 0).color(255, 0, 0, 100).endVertex();
				buffer.pos(cx + FTBChunks.TILE_SIZE, cy + FTBChunks.TILE_SIZE / 2F, 0).color(255, 0, 0, 100).endVertex();

				buffer.pos(cx, cy + FTBChunks.TILE_SIZE / 2F, 0).color(255, 0, 0, 100).endVertex();
				buffer.pos(cx + FTBChunks.TILE_SIZE / 2F, cy + FTBChunks.TILE_SIZE, 0).color(255, 0, 0, 100).endVertex();
			}

			tessellator.draw();
		}

		GlStateManager.enableTexture();
		GlStateManager.lineWidth(1F);

		String uuid = UUIDTypeAdapter.fromUUID(player.getUniqueID());
		ResourceLocation headTextureLocation = new ResourceLocation("uuid", uuid);
		Texture headTexture = texturemanager.getTexture(headTextureLocation);
		if (headTexture == null)
		{
			headTexture = new PlayerHeadTexture("https://minotar.net/avatar/" + uuid + "/8", ImageIcon.MISSING_IMAGE);
			texturemanager.loadTexture(headTextureLocation, headTexture);
		}

		double hx = sx + FTBChunks.TILE_SIZE * FTBChunks.TILE_OFFSET + MathUtils.mod(player.getPosX(), 16D);
		double hy = sy + FTBChunks.TILE_SIZE * FTBChunks.TILE_OFFSET + MathUtils.mod(player.getPosZ(), 16D);

		RenderSystem.bindTexture(headTexture.getGlTextureId());
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
		buffer.pos(hx - 4, hy + 4, 0.0D).color(255, 255, 255, 255).tex(0F, 1F).endVertex();
		buffer.pos(hx + 4, hy + 4, 0.0D).color(255, 255, 255, 255).tex(1F, 1F).endVertex();
		buffer.pos(hx + 4, hy - 4, 0.0D).color(255, 255, 255, 255).tex(1F, 0F).endVertex();
		buffer.pos(hx - 4, hy - 4, 0.0D).color(255, 255, 255, 255).tex(0F, 0F).endVertex();
		tessellator.draw();

		SendGeneralDataPacket d = FTBChunksClient.generalData;

		if (d != null)
		{
			List<ITextProperties> list = new ArrayList<>(4);
			list.add(new TranslationTextComponent("ftbchunks.gui.claimed"));
			list.add(new StringTextComponent(d.claimed + " / " + d.maxClaimed).mergeStyle(d.claimed > d.maxClaimed ? TextFormatting.RED : d.claimed == d.maxClaimed ? TextFormatting.YELLOW : TextFormatting.GREEN));
			list.add(new TranslationTextComponent("ftbchunks.gui.force_loaded"));
			list.add(new StringTextComponent(d.loaded + " / " + d.maxLoaded).mergeStyle(d.loaded > d.maxLoaded ? TextFormatting.RED : d.loaded == d.maxLoaded ? TextFormatting.YELLOW : TextFormatting.GREEN));

			for (int i = 0; i < list.size(); i++)
			{
				theme.drawString(matrixStack, list.get(i), 3, getScreen().getScaledHeight() - 10 * (list.size() - i) - 1, Color4I.WHITE, Theme.SHADOW);
			}
		}
	}
}