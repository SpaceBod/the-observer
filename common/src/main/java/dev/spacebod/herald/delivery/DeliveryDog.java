package dev.spacebod.herald.delivery;

import dev.spacebod.herald.HeraldContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The delivery dog. Appears somewhere the player can't see, trots up with the paper rolled up in its mouth,
 * drops it at their feet with a bark and bounds off, vanishing once out of sight. It can't be tamed or fed,
 * isn't saved with the world, and if anyone hits it, it drops the paper on the spot and runs.
 */
public class DeliveryDog extends PathfinderMob {
	// The classic wolf's sounds, by id: wolves pick their sounds per variant, and the variant sets vary by version.
	private static final SoundEvent BARK = wolfSound("ambient");
	private static final SoundEvent PANT = wolfSound("pant");
	private static final SoundEvent HURT = wolfSound("hurt");
	private static final SoundEvent DEATH = wolfSound("death");

	private static final double WALK_SPEED = 1.1;
	private static final double RUN_SPEED = 1.5;
	private static final double HAND_OVER_DISTANCE = 2.0;
	private static final int MIN_SPAWN_DISTANCE = 14;
	private static final int MAX_SPAWN_DISTANCE = 24;

	private @Nullable UUID recipient;
	private ItemStack paper = ItemStack.EMPTY;
	private int issue;
	private boolean routeChecked;
	private boolean leaving;
	private boolean delivered;
	private int leavingTicks;
	private @Nullable Vec3 exit;

	public DeliveryDog(EntityType<? extends DeliveryDog> type, Level level) {
		super(type, level);
		setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.MAX_HEALTH, 8)
				.add(Attributes.FOLLOW_RANGE, 48);
	}

	private static SoundEvent wolfSound(String name) {
		return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("entity.wolf." + name));
	}

	/** Sends a dog to {@code player}, or returns null if no hidden spot near them was found. */
	static @Nullable DeliveryDog dispatch(ServerPlayer player, ItemStack paper, int issue) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Mth.nextDouble(random, MIN_SPAWN_DISTANCE, MAX_SPAWN_DISTANCE);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos spot = findFooting(level, x, player.getBlockY(), z);
			if (spot == null || canSee(player, spot)) {
				continue;
			}
			DeliveryDog dog = HeraldContent.DELIVERY_DOG.create(level, EntitySpawnReason.EVENT);
			if (dog == null) {
				return null;
			}
			dog.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
			dog.recipient = player.getUUID();
			dog.issue = issue;
			dog.paper = paper;
			dog.setItemSlot(EquipmentSlot.MAINHAND, paper.copy());
			// The route is checked once it has landed: a mob that isn't standing on anything can't plan a walk.
			level.addFreshEntity(dog);
			return dog;
		}
		return null;
	}

	/** A standable spot within a few blocks of the player's height: solid floor, room above, no water. */
	private static @Nullable BlockPos findFooting(ServerLevel level, int x, int y, int z) {
		for (int i = 0; i <= 12; i++) {
			int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (!level.hasChunkAt(pos)) {
				return null;
			}
			BlockPos below = pos.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
					&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					&& level.getFluidState(pos).isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** Whether the player would see a dog standing at {@code pos}: in front of them with a clear line. */
	private static boolean canSee(ServerPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		Vec3 head = Vec3.atBottomCenterOf(pos).add(0, 0.7, 0);
		Vec3 toHead = head.subtract(eye).normalize();
		if (player.getViewVector(1f).dot(toHead) < 0.2) {
			return false;
		}
		HitResult hit = player.level().clip(new ClipContext(eye, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer target = recipient != null && level.getEntity(recipient) instanceof ServerPlayer p && p.isAlive() ? p : null;
		if (leaving || target == null) {
			runOff(target);
			return;
		}
		if (!routeChecked) {
			if (!onGround() && tickCount < 20) {
				return;
			}
			Path path = getNavigation().createPath(target, 1);
			if (path == null || !path.canReach()) {
				// No way through from here: vanish before anyone notices, and let the delivery try elsewhere.
				Delivery.routeFailed(target);
				discard();
				return;
			}
			routeChecked = true;
			getNavigation().moveTo(path, WALK_SPEED);
		}
		getLookControl().setLookAt(target, 30f, 30f);
		if (distanceToSqr(target) <= HAND_OVER_DISTANCE * HAND_OVER_DISTANCE) {
			handOver(target);
		} else if (tickCount % 10 == 0) {
			getNavigation().moveTo(target, WALK_SPEED);
		}
	}

	private void handOver(ServerPlayer target) {
		BehaviorUtils.throwItem(this, paper, target.position().add(0, 0.3, 0));
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		playSound(BARK, 1f, 1.1f);
		playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1f);
		paper = ItemStack.EMPTY;
		delivered = true;
		Delivery.received(target, issue);
		leave();
	}

	private void runOff(@Nullable ServerPlayer from) {
		leavingTicks++;
		if (exit == null || getNavigation().isDone()) {
			Vec3 away = from != null ? DefaultRandomPos.getPosAway(this, 24, 6, from.position()) : null;
			exit = away != null ? away : position().add(getRandom().nextGaussian() * 16, 0, getRandom().nextGaussian() * 16);
			getNavigation().moveTo(exit.x, exit.y, exit.z, RUN_SPEED);
		}
		boolean unseen = from == null || from.distanceToSqr(this) > 20 * 20 || !from.hasLineOfSight(this);
		if ((leavingTicks > 40 && unseen) || leavingTicks > 30 * 20) {
			discard();
		}
	}

	/** Stop delivering and head off. */
	void leave() {
		leaving = true;
	}

	boolean delivered() {
		return delivered;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!delivered && !paper.isEmpty() && recipient != null && level.getEntity(recipient) instanceof ServerPlayer target) {
			// Startled: the paper hits the ground here, and it's off.
			spawnAtLocation(level, paper);
			setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			paper = ItemStack.EMPTY;
			delivered = true;
			Delivery.received(target, issue);
		}
		leave();
		return super.hurtServer(level, source, damage);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Only the delivery decides when it goes.
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		// It has its mouth full until the paper is delivered.
		return delivered ? BARK : PANT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DEATH;
	}
}
