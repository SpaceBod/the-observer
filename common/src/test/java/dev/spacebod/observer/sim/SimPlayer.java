package dev.spacebod.observer.sim;

import dev.spacebod.observer.demo.Resident;

/** A resident of a simulated world: their events happen on the world's current day. */
public final class SimPlayer extends Resident {
	SimPlayer(SimWorld world, String name) {
		super(name, () -> world.day);
	}
}
