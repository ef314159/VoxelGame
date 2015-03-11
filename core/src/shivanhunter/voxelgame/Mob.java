package shivanhunter.voxelgame;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class Mob {
	private World world;
	private Vector3 position;
	private Vector3 velocity = new Vector3(0,0,0);
	
	protected float angleX = 0;
	protected float angleY = 0;
	
	private float radius;
	private float height;
	private float stepHeight;
	private float speed;
	private float jumpForce = 14;
	
	boolean grounded = false;
	
	private static float AIR_CONTROL = 0.25f;
	
	protected ModelInstance instance;
	protected boolean affectedByGravity = true;
	
	protected static int FORWARD = 0, LEFT = 1, BACK = 2, RIGHT = 3;
	protected boolean[] movingDir = new boolean[4];
	protected boolean jumping;
	protected boolean moving = false;
	protected boolean hasJetpack = true;
	
	public Mob(World world, Vector3 position, float radius, float height, float speed) {
		this.world = world;
		this.position = position;
		this.radius = radius;
		this.height = height;
		this.stepHeight = 1;
		this.speed = speed;
	
		instance = new ModelInstance(new ModelBuilder().createBox(
				radius*2, height, radius*2, 
				new Material(ColorAttribute.createDiffuse(Color.BLUE)),
				Usage.Position | Usage.Normal));
		instance.transform.translate(position);
		instance.transform.translate(0, height/2, 0);
	}
	
	public void render(Environment environment, ModelBatch batch) {
		batch.render(instance, environment);
	}
	
	public void update(float delta) {
		updatePhysics(delta);
		updateModel();
	}
	
	Vector3 forwardVec = new Vector3(0, 0, 0),
			rightVec = new Vector3(0, 0, 0),
			upVec = new Vector3(0, 1, 0);
	
	protected boolean updatePhysics(float delta) {
		boolean collision = false;
		float airControl;
		if (hasJetpack && jumping) {
			airControl = 4f;
		} else {
			airControl = AIR_CONTROL;
		}
		
		if (affectedByGravity) velocity.y += world.gravity*delta;
		
		forwardVec.x = MathUtils.cos(angleX);
		forwardVec.z = MathUtils.sin(angleX);
		rightVec.x = -MathUtils.sin(angleX);
		rightVec.z = MathUtils.cos(angleX);
		
		moving = movingDir[FORWARD] ||
				movingDir[LEFT] ||
				movingDir[BACK] ||
				movingDir[RIGHT];
		
		if (grounded) {
			if (movingDir[FORWARD]) velocity.mulAdd(forwardVec, speed);
			if (movingDir[LEFT]) velocity.mulAdd(rightVec, -speed);
			if (movingDir[BACK]) velocity.mulAdd(forwardVec, -speed);
			if (movingDir[RIGHT]) velocity.mulAdd(rightVec, speed);
			if (jumping) velocity.mulAdd(upVec, jumpForce);
			
			if (moving) {
				velocity.x *= .618f;
				velocity.z *= .618f;
			} else {
				velocity.scl(1 - 10f*delta);
			}
		} else {
			if (movingDir[FORWARD]) velocity.mulAdd(forwardVec, speed*airControl*delta);
			if (movingDir[LEFT]) velocity.mulAdd(rightVec, -speed*airControl*delta);
			if (movingDir[BACK]) velocity.mulAdd(forwardVec, -speed*airControl*delta);
			if (movingDir[RIGHT]) velocity.mulAdd(rightVec, speed*airControl*delta);
			if (jumping && hasJetpack) velocity.mulAdd(upVec, jumpForce*airControl*delta);
			
			velocity.scl(1 - .2f*delta);
		}
		
		float dx = velocity.x*delta,
			  dy = velocity.y*delta,
			  dz = velocity.z*delta;
		
		position.x += dx;
		position.z += dz;
		
		if (world.collide(this) > position.y + stepHeight) {
			collision = true;
			// try just x movement
			position.z -= dz;
			
			if (world.collide(this) > position.y + stepHeight) {
				// x failed, try z movement
				position.z += dz;
				position.x -= dx;
				velocity.x = 0;
				
				if (world.collide(this) > position.y + stepHeight) {
					// z failed, zero velocity
					position.z -= dz;
					velocity.z = 0;
				}
			} else {
				velocity.z = 0;
			}
		}

		position.y += dy;
		float ground = world.collide(this);
		//position.y = Math.max(ground, position.y + dy);
		
		if (ground > position.y + height) {
			collision = true;
			position.y -= dy;
			velocity.y = 0;
		} else if (ground + 0.01f > position.y) {
			collision = true;
			grounded = true;
			velocity.y = 0;
			position.y = ground;
		} else {
			grounded = false;
		}
		return collision;
	}
	
	protected void updateModel() {
		instance.transform.setToTranslation(position.x, position.y + height/2, position.z);
		instance.transform.rotateRad(Vector3.Y, -angleX);
	}
	
	public Vector3 getPosition() {
		return position;
	}
	
	public Vector3 getVelocity() {
		return velocity;
	}
	
	public float getAngleX() {
		return angleX;
	}
	
	public float getAngleY() {
		return angleY;
	}
	
	public float getRadius() {
		return radius;
	}
	
	public float getHeight() {
		return height;
	}
}
