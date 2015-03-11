package shivanhunter.voxelgame;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Player extends Mob implements InputProcessor {
	private boolean[] keys = new boolean[256];

	public Player(World world, Vector3 position, float radius, float height, float speed) {
		super(world, position, radius, height, speed);
	}
	
	public void render(Environment environment, ModelBatch batch) {
		// do nothing, it's the goddamn player character
	}
	
	public void update(float delta) {
		getInput();
		super.update(delta);
	}
	
	private void getInput() {
		if (keys[Keys.W]) movingDir[FORWARD] = true;
		else movingDir[FORWARD] = false;
		if (keys[Keys.A]) movingDir[LEFT] = true;
		else movingDir[LEFT] = false;
		if (keys[Keys.S]) movingDir[BACK] = true;
		else movingDir[BACK] = false;
		if (keys[Keys.D]) movingDir[RIGHT] = true;
		else movingDir[RIGHT] = false;
		if (keys[Keys.SPACE]) jumping = true;
		else jumping = false;
	}

	
	/*
	 * INPUT FUNCTIONS
	 */

	@Override public boolean keyDown(int keycode) {
		keys[keycode] = true;
		return true;
	}

	@Override public boolean keyUp(int keycode) {
		keys[keycode] = false;
		return true;
	}

	private int oldScreenX, oldScreenY;
	private boolean oldPosition = false;
	
	@Override public boolean mouseMoved(int screenX, int screenY) {
		if (oldPosition) {
			int dx = screenX - oldScreenX;
			int dy = screenY - oldScreenY;
			
			angleX += dx/100f;
			angleY -= dy/100f;
			angleY = MathUtils.clamp(angleY, -MathUtils.PI/2+.01f, MathUtils.PI/2-.01f);
		}
		oldScreenX = screenX;
		oldScreenY = screenY;
		oldPosition = true;
		
		return true;
	}

	@Override public boolean keyTyped(char character) {return false;}
	@Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {return false;}
	@Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {return false;}
	@Override public boolean touchDragged(int screenX, int screenY, int pointer) {return false;}
	@Override public boolean scrolled(int amount) {return false;}
}
