package server;

public class DrunkBot extends Bot {

	public DrunkBot(int id, float x, float y, String name) {
		super(id, x, y, name);
		this.MOVE_DIRECTION_MEMORY = 100;
		this.RANDOMNESS = 100;
		this.speed *= 0.75;
	}

}