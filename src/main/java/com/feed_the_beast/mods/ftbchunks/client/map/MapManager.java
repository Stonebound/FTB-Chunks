package com.feed_the_beast.mods.ftbchunks.client.map;

import com.feed_the_beast.mods.ftbchunks.ColorMapLoader;
import com.feed_the_beast.mods.ftbchunks.client.FTBChunksClient;
import com.feed_the_beast.mods.ftbchunks.client.map.color.BlockColor;
import com.feed_the_beast.mods.ftbchunks.core.BiomeFTBC;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author LatvianModder
 */
public class MapManager implements MapTask
{
	public static MapManager inst;

	public final UUID serverId;
	public final Path directory;
	private final Map<RegistryKey<World>, MapDimension> dimensions;
	public boolean saveData;

	private final Int2ObjectOpenHashMap<ResourceLocation> blockColorIndexMap;
	private final Object2IntOpenHashMap<ResourceLocation> blockColorIndexMapReverse;
	private final Int2ObjectOpenHashMap<RegistryKey<Biome>> biomeColorIndexMap;
	private final Int2ObjectOpenHashMap<BlockColor> blockIdToColCache;
	private final List<BiomeFTBC> biomesToRelease;

	public MapManager(UUID id, Path dir)
	{
		serverId = id;
		directory = dir;
		dimensions = new LinkedHashMap<>();
		saveData = false;

		blockColorIndexMap = new Int2ObjectOpenHashMap<>();
		blockColorIndexMap.defaultReturnValue(new ResourceLocation("minecraft:air"));
		blockColorIndexMapReverse = new Object2IntOpenHashMap<>();
		blockColorIndexMapReverse.defaultReturnValue(0);
		biomeColorIndexMap = new Int2ObjectOpenHashMap<>();
		biomeColorIndexMap.defaultReturnValue(Biomes.PLAINS);

		blockIdToColCache = new Int2ObjectOpenHashMap<>();

		biomesToRelease = new ArrayList<>();

		try
		{
			Path dimFile = directory.resolve("dimensions.txt");

			if (Files.exists(dimFile))
			{
				for (String s : Files.readAllLines(dimFile))
				{
					s = s.trim();

					if (s.length() >= 3)
					{
						RegistryKey<World> key = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, new ResourceLocation(s));
						dimensions.put(key, new MapDimension(this, key));
					}
				}
			}
			else
			{
				saveData = true;
			}

			Path blockFile = directory.resolve("block_map.txt");

			if (Files.exists(blockFile))
			{
				for (String s : Files.readAllLines(blockFile))
				{
					s = s.trim();

					if (!s.isEmpty())
					{
						String[] s1 = s.split(" ", 2);
						int i = Integer.decode(s1[0]);
						ResourceLocation loc = new ResourceLocation(s1[1]);
						blockColorIndexMap.put(i, loc);
						blockColorIndexMapReverse.put(loc, i);
					}
				}
			}
			else
			{
				saveData = true;
			}

			Path biomeFile = directory.resolve("biome_map.txt");

			if (Files.exists(biomeFile))
			{
				for (String s : Files.readAllLines(biomeFile))
				{
					s = s.trim();

					if (!s.isEmpty())
					{
						String[] s1 = s.split(" ", 2);
						int i = Integer.decode(s1[0]);
						ResourceLocation loc = new ResourceLocation(s1[1]);
						RegistryKey<Biome> key = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, loc);
						biomeColorIndexMap.put(i, key);
					}
				}
			}
			else
			{
				saveData = true;
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public Map<RegistryKey<World>, MapDimension> getDimensions()
	{
		return dimensions;
	}

	public MapDimension getDimension(RegistryKey<World> dim)
	{
		return getDimensions().computeIfAbsent(dim, d -> new MapDimension(this, d).created());
	}

	public void release()
	{
		for (MapDimension dimension : getDimensions().values())
		{
			dimension.release();
		}

		for (BiomeFTBC b : biomesToRelease)
		{
			b.setFTBCBiomeColorIndex(-1);
		}

		biomesToRelease.clear();
		blockIdToColCache.clear();
	}

	public void updateAllRegions(boolean save)
	{
		for (MapDimension dimension : getDimensions().values())
		{
			for (MapRegion region : dimension.getRegions().values())
			{
				region.update(save);
			}
		}

		FTBChunksClient.updateMinimap = true;
	}

	@Override
	public void runMapTask()
	{
		try
		{
			Files.write(directory.resolve("dimensions.txt"), dimensions
					.keySet()
					.stream()
					.map(key -> key.getLocation().toString())
					.collect(Collectors.toList())
			);

			Files.write(directory.resolve("block_map.txt"), blockColorIndexMap
					.int2ObjectEntrySet()
					.stream()
					.sorted(Map.Entry.comparingByValue())
					.map(key -> String.format("#%06X %s", key.getIntKey(), key.getValue()))
					.collect(Collectors.toList())
			);

			Files.write(directory.resolve("biome_map.txt"), biomeColorIndexMap
					.int2ObjectEntrySet()
					.stream()
					.sorted(Comparator.comparing(o -> o.getValue().getLocation()))
					.map(key -> String.format("#%03X %s", key.getIntKey(), key.getValue().getLocation()))
					.collect(Collectors.toList())
			);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	public int getBlockColorIndex(ResourceLocation id)
	{
		int i = blockColorIndexMapReverse.getInt(id);

		if (i == 0)
		{
			Random random = new Random((long) id.getNamespace().hashCode() & 4294967295L | ((long) id.getPath().hashCode() & 4294967295L) << 32);
			i = id.hashCode() & 0xFFFFFF;

			while (i == 0 || blockColorIndexMap.containsKey(i))
			{
				i = random.nextInt() & 0xFFFFFF;
			}

			blockColorIndexMap.put(i, id);
			blockColorIndexMapReverse.put(id, i);
			saveData = true;
		}

		return i;
	}

	public int getBiomeColorIndex(World world, Biome biome, Object b0)
	{
		BiomeFTBC b = b0 instanceof BiomeFTBC ? (BiomeFTBC) b0 : null;

		if (b == null)
		{
			return 0;
		}

		int i = b.getFTBCBiomeColorIndex();

		if (i == -1)
		{
			RegistryKey<Biome> key = world.func_241828_r().getRegistry(Registry.BIOME_KEY).getOptionalKey(biome).orElse(null);

			if (key == null)
			{
				b.setFTBCBiomeColorIndex(0);
				return 0;
			}

			for (Int2ObjectOpenHashMap.Entry<RegistryKey<Biome>> entry : biomeColorIndexMap.int2ObjectEntrySet())
			{
				if (entry.getValue() == key)
				{
					i = entry.getIntKey();
					b.setFTBCBiomeColorIndex(i);
					return i;
				}
			}

			Random random = new Random((long) key.getLocation().getNamespace().hashCode() & 4294967295L | ((long) key.getLocation().getPath().hashCode() & 4294967295L) << 32);
			i = key.getLocation().hashCode() & 0b111_11111111;

			while (i == 0 || biomeColorIndexMap.containsKey(i))
			{
				i = random.nextInt() & 0b111_11111111;
			}

			biomeColorIndexMap.put(i, key);
			b.setFTBCBiomeColorIndex(i);
			saveData = true;
			biomesToRelease.add(b);
		}

		return i;
	}

	public Block getBlock(int id)
	{
		ResourceLocation rl = blockColorIndexMap.get(id & 0xFFFFFF);
		Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
		return block == null ? Blocks.AIR : block;
	}

	public BlockColor getBlockColor(int id)
	{
		return blockIdToColCache.computeIfAbsent(id & 0xFFFFFF, i -> ColorMapLoader.getBlockColor(blockColorIndexMap.get(i)));
	}

	public RegistryKey<Biome> getBiomeKey(int id)
	{
		return biomeColorIndexMap.get(id & 0b111_11111111);
	}

	public Biome getBiome(World world, int id)
	{
		return world.func_241828_r().getRegistry(Registry.BIOME_KEY).getValueForKey(getBiomeKey(id));
	}
}