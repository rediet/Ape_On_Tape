package server.model.map;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import server.exceptions.MapParseException;
import server.model.entities.Barrier;
import server.model.entities.CloudTrap;
import server.model.entities.Entity;
import server.model.entities.FastShoot;
import server.model.entities.PlayerFinish;
import server.model.entities.SpikeTrap;
import server.model.entities.Turret;
import server.model.entities.moving.AggroBot;
import server.model.entities.moving.Bot;
import server.model.entities.moving.DrunkBot;
import server.util.IdFactory;

import com.google.gson.Gson;

public class MapInfo {

	// the layer names that need to be present
	public static final String FOREGROUND = "foreground";
	public static final String BACKGROUND = "background"; // (unused)
	public static final String ENTITIES = "entities";

	// defines the entity type of a symbol TODO: set up
	public static final PositionType[] entitySymbols = {//
	PositionType.PlayerStart, // symbol #01
			PositionType.PlayerFinish, // symbol #02
			PositionType.None, // symbol #03
			PositionType.None, // symbol #04
			PositionType.Bot, // symbol #05
			PositionType.DrunkBot, // symbol #06
			PositionType.AggroBot, // symbol #07
			PositionType.Turret, // symbol #08
			PositionType.Barrier, // symbol #09
			PositionType.None, // symbol #10
			PositionType.FastShoot, // symbol #11
			PositionType.CloudTrap, // symbol #12
			PositionType.SpikeTrap, // symbol #13
			PositionType.LookingDirUp, // symbol #14
			PositionType.LookingDirRight, // symbol #15
			PositionType.LookingDirLeft, // symbol #16
			PositionType.LookingDirDown, // symbol #17
	};

	// DYNAMIC FIELDS
	private int[][] collisionMap;
	private Map<PositionType, List<EntityInfo>> entities;

	// CONSTRUCTOR
	public MapInfo() {
		this.entities = new HashMap<PositionType, List<EntityInfo>>();
	}

	public void setCollisionMap(int[][] map) {
		this.collisionMap = map;
	}

	public int[][] getCollisionMap() {
		return this.collisionMap;
	}

	public Map<PositionType, List<EntityInfo>> getPositionsMap() {
		return this.entities;
	}

	public List<EntityInfo> getPositions(PositionType type) {
		return this.entities.get(type);
	}

	public boolean containsType(PositionType type) {
		return this.entities.containsKey(type);
	}

	/**
	 * Commit all entities that belong to the same tile which means: Searches
	 * for PositionTypes that specify a looking direction and adds the
	 * information (if present) to all the other entities in that tile. Adds
	 * valid entities to the entities map.
	 * 
	 * @param tileEntities
	 */
	public void commitTileEntities(List<EntityInfo> tileEntities) {
		PositionType t;

		// check if one of the EntityInfo types is a looking direction
		boolean hasLookingDirs = false;
		Point lookingDir = new Point(0, 0);
		Point tempDir = null;
		for (EntityInfo entity : tileEntities) {
			t = entity.getType();
			if (PositionType.isLookingType(t)) {
				tempDir = PositionType.createLookingDirection(t);
				// incrementally set direction
				lookingDir.x += tempDir.x;
				lookingDir.y += tempDir.y;
				hasLookingDirs = true;
			}
		}

		for (EntityInfo entity : tileEntities) {
			t = entity.getType();
			if (PositionType.None.equals(t) || PositionType.isLookingType(t))
				continue; // skip invalid entities
			if (hasLookingDirs)
				entity.setLookingDirection(lookingDir);
			if (!entities.containsKey(t)) {
				entities.put(t, new ArrayList<EntityInfo>());
			}
			this.entities.get(t).add(entity);
		}
	}

	public List<Entity> createEntities(TileMap map) {
		List<Entity> entities = new LinkedList<Entity>();
		for (PositionType type : PositionType.values()) {
			if (this.containsType(type))
				entities.addAll(this.createEntitesOfType(type, map));
		}
		return entities;
	}

	private List<Entity> createEntitesOfType(PositionType type, TileMap map) {
		List<Entity> entities = new LinkedList<Entity>();
		for (EntityInfo entity : this.getPositions(type)) {
			Entity e = this.createEntity(entity, map);
			if (e != null)
				entities.add(e);
		}
		return entities;
	}

	private Entity createEntity(EntityInfo entityInfo, TileMap map) {
		PositionType type = entityInfo.getType();
		Point position = entityInfo.getPosition();

		Entity entity = null;
		float x = position.x * map.getTileWidth();
		float y = position.y * map.getTileHeight();
		switch (type) {
		case Barrier:
			entity = new Barrier(x, y);
			break;
		case Turret:
			entity = new Turret(x, y);
			break;
		case Bot:
			entity = new Bot(IdFactory.getNextId(), x, y, "Eduardo");
			break;
		case DrunkBot:
			entity = new DrunkBot(IdFactory.getNextId(), x, y, "Oskar");
			break;
		case AggroBot:
			entity = new AggroBot(IdFactory.getNextId(), x, y, "Remo");
			break;
		case PlayerStart:
			break;
		case PlayerFinish:
			entity = new PlayerFinish(x, y, "finish_flag");
			break;
		case SpikeTrap:
			entity = new SpikeTrap(x, y);
			break;
		case CloudTrap:
			entity = new CloudTrap(x, y);
			break;
		case FastShoot:
			entity = new FastShoot(x, y);
			break;
		default: // gr8, thx
			System.err.println("WARNING the specified type '" + type
					+ "' doesn't get created yet. See MapInfo.createEntity");
			break;
		}

		if (entity != null && entityInfo.hasLookingDirection()) {
			entity.setDirX(entityInfo.getLookingDirection().x);
			entity.setDirY(entityInfo.getLookingDirection().y);
		}

		return entity;
	}

	public static PositionType getEntityType(int number, int entityFirstgid) {
		int index = number - entityFirstgid;
		if (index < 0 || index >= entitySymbols.length)
			return PositionType.None;
		else
			return entitySymbols[index];
	}

	public static MapInfo fromJSON(String filename) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			Gson json = new Gson();
			return parseMap(json.fromJson(br, JsonMap.class));
		} catch (FileNotFoundException e) {
			System.err.println("File not found!: \"" + filename + "\"");
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (MapParseException e) {
			System.err.println("Could not parse JSON map:");
			System.err.println(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	private static MapInfo parseMap(JsonMap map) throws MapParseException {
		MapInfo mapInfo = new MapInfo();

		// == GENERATE COLLISION MAP =====================================
		JsonMapLayer layer = getLayer(FOREGROUND, map);

		// set collision map properties
		int subdivisions = getSubdivisions(map);
		int width = map.width / subdivisions;
		int height = map.height / subdivisions;
		int[] data = layer.data;

		// parse collision map 'foreground' layer (that consists of sub-tiles)
		// to binary collision map of full-tiles
		int[][] collisionMap = new int[height][width];
		int iRow1, iRow2;
		int subCollisionCount;
		int iOff = Math.abs((2 - subdivisions) / 2);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				subCollisionCount = 0;
				// logic: a collision is defined if a square of 4 subtiles that
				// is placed in the middle of a tile has more than 1
				// occupied subtiles
				iRow1 = (y * subdivisions + iOff) * map.width
						+ (x * subdivisions + iOff);
				iRow2 = (y * subdivisions + iOff + 1) * map.width
						+ (x * subdivisions + iOff);
				if (data[iRow1] != 0)
					subCollisionCount++;
				if (data[iRow1 + 1] != 0)
					subCollisionCount++;
				if (data[iRow2] != 0)
					subCollisionCount++;
				if (data[iRow2 + 1] != 0)
					subCollisionCount++;
				collisionMap[y][x] = (subCollisionCount > 1) ? 1 : 0;
			}
		}

		mapInfo.setCollisionMap(collisionMap);

		// == GENERATE ENTITY STARTING POSITIONS =========================
		int entityFirstgid = 0;
		// search for displacement index for entity symbol numbers (depending on
		// tileset and it's "firstgid")
		for (JsonTileSet tileSet : map.tilesets) {
			if (ENTITIES.equals(tileSet.name)) {
				entityFirstgid = tileSet.firstgid;
			}
		}

		layer = getLayer(ENTITIES, map);
		data = layer.data;
		PositionType type;
		int dIndex;
		List<EntityInfo> tileCache = new LinkedList<EntityInfo>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				tileCache.clear();
				// search for all defined entities in each subtile of the
				// current full tile and cache them
				for (int i = 0; i < subdivisions; i++) {
					for (int j = 0; j < subdivisions; j++) {
						dIndex = (y * subdivisions + j) * map.width
								+ (x * subdivisions + i);
						if (data[dIndex] != 0) {
							type = getEntityType(data[dIndex], entityFirstgid);
							tileCache.add(new EntityInfo(x, y, type));
						}
					}
				}
				// commit all entities in this tile
				mapInfo.commitTileEntities(tileCache);
			}
		}

		return mapInfo;
	}

	private static JsonMapLayer getLayer(String layerName, JsonMap map)
			throws MapParseException {
		JsonMapLayer layer = null;
		for (JsonMapLayer l : map.layers) {
			if (l.name.equals(layerName))
				layer = l;
		}
		if (layer == null) {
			throw MapParseException.noLayer(layerName);
		}
		return layer;
	}

	private static int getSubdivisions(JsonMap map) throws MapParseException {
		int subdivisions = map.properties.subdivision;
		if (subdivisions == 0)
			throw MapParseException.noProperty("subdivision");
		return subdivisions;
	}

	// == JSON FILE REPRESENTATION OF A MAP GENERATED USING TILED MAP EDITOR ==
	private static class JsonMap {
		int width;
		int height;
		JsonMapProperties properties;
		List<JsonMapLayer> layers;
		List<JsonTileSet> tilesets;
	}

	private static class JsonMapLayer {
		int[] data;
		String name;
	}

	private static class JsonTileSet {
		int firstgid;
		int tilewidth;
		int tileheight;
		int imagewidth;
		int imageheight;
		String name;
		String image;
	}

	private static class JsonMapProperties {
		int subdivision;
	}
	// == END FILE REPRESENTATION =============================================

}
