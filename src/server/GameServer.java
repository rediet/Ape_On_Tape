package server;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.webbitserver.BaseWebSocketHandler;
import org.webbitserver.WebSocketConnection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GameServer extends BaseWebSocketHandler {

	Gson json = new GsonBuilder().setExclusionStrategies(
			new GsonExclusionStrategy(String.class)).create();
	public static final String ID_KEY = "id";
	public GameHandler gameHandler;

	static class Incoming {
		enum Action {
			LOGIN, ROOM, SAY, KEYS_PRESSED
		}

		Action action;
		String loginUsername;
		String roomJoin;
		String message;
		List<Integer> keysPressed;
	}

	static class Outgoing {
		enum Action {
			JOIN, LEAVE, SAY, UPDATE, INIT_GAME, ROOMS, NEW_ROOM
		}

		Action action;
		String username;
		String message;
		Map<Integer, Entity> entities;
		int[][] map;
		int playerId;
		String[] rooms;
		String newRoom;
		String[] soundEvents;
		public boolean gameRunning;
	}

	public GameServer(GameHandler gameHandler) {
		super();
		this.gameHandler = gameHandler;
	}

	private Set<WebSocketConnection> connections = new HashSet<WebSocketConnection>();

	@Override
	public void onOpen(WebSocketConnection connection) {
		connections.add(connection);
	}

	@Override
	public void onMessage(WebSocketConnection connection, String msg) {
		Incoming incoming = json.fromJson(msg, Incoming.class);
		switch (incoming.action) {
		case LOGIN:
			login(connection, incoming.loginUsername);
			break;
		case ROOM:
			leaveCurrentRoom(connection);
			joinRoom(connection, incoming.roomJoin);
			break;
		// case SAY:
		// say(connection, incoming.message);
		// break;
		case KEYS_PRESSED:
			this.gameHandler.setKeysPressed((Integer) connection.data(ID_KEY),
					incoming.keysPressed);
			break;
		}
	}

	private int getCurrentId() {
		return IdFactory.getNextId();
	}

	private void login(WebSocketConnection connection, String username) {
		int id = this.getCurrentId();
		connection.data(ID_KEY, id); // associate username with connection
		gameHandler.playerLogin(id, username);
	}

	private void joinRoom(WebSocketConnection connection, String roomJoin) {
		int id = (Integer) connection.data().get(ID_KEY);
		gameHandler.joinRoom(id, roomJoin);
	}

	private void leaveCurrentRoom(WebSocketConnection connection) {
		int id = (Integer) connection.data().get(ID_KEY);
		gameHandler.leaveCurrentRoom(id);
	}

	public void sendJoinMessage(int id, String user, String roomJoin,
			List<Integer> receipants) {
		Outgoing outgoing = new Outgoing();
		outgoing.action = Outgoing.Action.JOIN;
		outgoing.username = user;
		this.sendGameInfo(findConnection(id), gameHandler.getGameMap(roomJoin));
		broadcast(outgoing, receipants);
	}

	private WebSocketConnection findConnection(int id) {
		WebSocketConnection connection = null;
		for (WebSocketConnection c : connections) {
			if (new Integer(id).equals(c.data(ID_KEY))) {
				connection = c;
			}
		}
		if (connection == null)
			throw new RuntimeException("No connection for specified ID");
		return connection;
	}

	// private void say(WebSocketConnection connection, String message) {
	// String username = (String) connection.data(ID_KEY);
	// if (username != null) {
	// Outgoing outgoing = new Outgoing();
	// outgoing.action = Outgoing.Action.SAY;
	// outgoing.username = username;
	// outgoing.message = message;
	// broadcast(outgoing, receipants);
	// }
	// }

	public void update(boolean gameRunning, Map<Integer, Entity> entities, String[] soundEvents) {
		Outgoing outgoing = new Outgoing();
		if (gameRunning)
			outgoing.gameRunning = gameRunning;
		outgoing.action = Outgoing.Action.UPDATE;
		outgoing.entities = entities;
		outgoing.soundEvents = soundEvents;
		broadcast(outgoing, entities.keySet());
	}

	public void sendGameInfo(WebSocketConnection connection, int[][] map) {
		Outgoing outgoing = new Outgoing();
		outgoing.action = Outgoing.Action.INIT_GAME;
		outgoing.playerId = (Integer) connection.data().get(ID_KEY);
		outgoing.map = map;
		String jsonStr = this.json.toJson(outgoing);
		connection.send(jsonStr);
	}

	private void broadcast(Outgoing outgoing, Collection<Integer> playerIds) {
		String jsonStr = this.json.toJson(outgoing);
		for (WebSocketConnection connection : connections) {
			if (playerIds.contains(connection.data(ID_KEY))) {
				// only broadcast to those who have completed login
				connection.send(jsonStr);
			}
		}
	}

	@Override
	public void onClose(WebSocketConnection connection) {
		int id = (Integer) connection.data(ID_KEY);
		gameHandler.playerDisconnected(id);
	}

	public void sendDisconnectMessage(int id, String user,
			List<Integer> receipents) {
		Outgoing outgoing = new Outgoing();
		outgoing.action = Outgoing.Action.LEAVE;
		outgoing.username = user;
		broadcast(outgoing, receipents);
	}

	public void disconnect(int id) {
		WebSocketConnection connection = findConnection(id);
		connections.remove(connection);

	}

	public void sendRoomList(Collection<String> rooms,
			Collection<Integer> receipent) {
		Outgoing outgoing = new Outgoing();
		outgoing.action = Outgoing.Action.ROOMS;
		outgoing.rooms = rooms.toArray(new String[0]);
		broadcast(outgoing, receipent);
	}

	public void sendNewRoomInfo(String newRoomName,
			Collection<Integer> receipents) {
		Outgoing outgoing = new Outgoing();
		outgoing.action = Outgoing.Action.NEW_ROOM;
		outgoing.newRoom = newRoomName;
		broadcast(outgoing, receipents);
	}
}