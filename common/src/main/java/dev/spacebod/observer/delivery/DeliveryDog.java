package dev.spacebod.observer.delivery;

import dev.spacebod.observer.news.Newsroom;
import dev.spacebod.observer.ObserverContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The Pawstman, the delivery dog. Appears somewhere the player can't see and trots up with the paper rolled up
 * in its mouth. If it charges a fee it then follows its reader, barking, until they use it to open its counter
 * and pay, or turn it away (there, by hitting it, or by keeping it waiting a minute); then it leaves with the
 * paper and the copy waits at the newsstands. Once paid (or for free) it drops the paper at their feet and
 * bounds off, vanishing once out of sight. It can't be tamed and isn't saved with the world.
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
	/** How long it waits to be paid before giving up. */
	private static final int PAY_WAIT_TICKS = 60 * 20;
	private static final double FOLLOW_DISTANCE = 2.5;
	/** Like a tamed wolf: this far behind, and no longer catching up, it teleports to its reader. */
	private static final double TELEPORT_DISTANCE = 12;

	private @Nullable UUID recipient;
	private ItemStack paper = ItemStack.EMPTY;
	private int issue;
	private boolean routeChecked;
	private boolean leaving;
	private boolean delivered;
	private int fee;
	private int paid;
	private boolean awaitingPayment;
	private boolean gaveUp;
	private int waitTicks;
	private boolean counterOpen;
	/** Distance to the reader at the last progress check, to notice when it has stopped catching up. */
	private double lastDistance = Double.MAX_VALUE;
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
			return create(level, player, spot, paper, issue);
		}
		// Nowhere out of sight to appear: it turns up beside the reader instead.
		BlockPos beside = besideReader(level, player);
		return beside != null ? create(level, player, beside, paper, issue) : null;
	}

	private static @Nullable DeliveryDog create(ServerLevel level, ServerPlayer player, BlockPos spot, ItemStack paper, int issue) {
		DeliveryDog dog = ObserverContent.DELIVERY_DOG.create(level, EntitySpawnReason.EVENT);
		if (dog == null) {
			return null;
		}
		dog.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getRandom().nextFloat() * 360f, 0f);
		dog.recipient = player.getUUID();
		dog.issue = issue;
		dog.fee = Newsroom.priceFor(player);
		dog.setCustomName(Component.translatable("entity.spacebod_observer.delivery_dog"));
		dog.paper = paper;
		dog.setItemSlot(EquipmentSlot.MAINHAND, paper.copy());
		// The route is checked once it has landed: a mob that isn't standing on anything can't plan a walk.
		level.addFreshEntity(dog);
		return dog;
	}

	/** A spot two or three blocks from the reader to stand on, as a tamed wolf teleporting to its owner picks. */
	private static @Nullable BlockPos besideReader(ServerLevel level, ServerPlayer player) {
		RandomSource random = player.getRandom();
		BlockPos base = player.blockPosition();
		for (int attempt = 0; attempt < 16; attempt++) {
			int dx = Mth.randomBetweenInclusive(random, -3, 3);
			int dz = Mth.randomBetweenInclusive(random, -3, 3);
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			BlockPos pos = base.offset(dx, Mth.randomBetweenInclusive(random, -1, 1), dz);
			if (level.hasChunkAt(pos) && standable(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty();
	}

	/** Jumps to its reader's side, like a tamed wolf left behind. False if there was nowhere to stand. */
	private boolean teleportTo(ServerLevel level, ServerPlayer target) {
		BlockPos spot = besideReader(level, target);
		if (spot == null) {
			return false;
		}
		snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYRot(), getXRot());
		getNavigation().stop();
		lastDistance = Double.MAX_VALUE;
		return true;
	}

	/** A standable spot within a few blocks of the player's height: solid floor, room above, no water. */
	private static @Nullable BlockPos findFooting(ServerLevel level, int x, int y, int z) {
		for (int i = 0; i <= 12; i++) {
			int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (!level.hasChunkAt(pos)) {
				return null;
			}
			if (standable(level, pos)) {
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
			routeChecked = true;
			if (path == null || !path.canReach()) {
				// No way through from here: like a wolf, it turns up at its reader's side.
				if (!teleportTo(level, target)) {
					Delivery.routeFailed(target);
					discard();
					return;
				}
			} else {
				getNavigation().moveTo(path, WALK_SPEED);
			}
		}
		if (tickCount % 40 == 0) {
			double distance = distanceTo(target);
			if (distance >= TELEPORT_DISTANCE && (distance > lastDistance - 1 || getNavigation().isDone())) {
				teleportTo(level, target);
			}
			lastDistance = distanceTo(target);
		}
		getLookControl().setLookAt(target, 30f, 30f);
		if (awaitingPayment) {
			awaitPayment(level, target);
			return;
		}
		if (distanceToSqr(target) <= HAND_OVER_DISTANCE * HAND_OVER_DISTANCE) {
			if (fee > paid && target.getInventory().countItem(Items.EMERALD) < fee) {
				// Nothing to pay with: no pestering, the copy waits at the newsstands.
				playSound(BARK, 1f, 1f);
				giveUp(level, target, "broke");
			} else {
				awaitingPayment = true;
				setCustomNameVisible(true);
				playSound(BARK, 1f, 1f);
				askForFee(target);
			}
		} else if (tickCount % 10 == 0) {
			getNavigation().moveTo(target, WALK_SPEED);
		}
	}

	/** Sits at the reader's heels, barking now and then, and takes emeralds dropped at its paws. */
	private void awaitPayment(ServerLevel level, ServerPlayer target) {
		if (!counterOpen) {
			waitTicks++;
		}
		if (distanceToSqr(target) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
			if (tickCount % 10 == 0) {
				getNavigation().moveTo(target, WALK_SPEED);
			}
		} else {
			getNavigation().stop();
		}
		if (!counterOpen && waitTicks % 60 == 0) {
			playSound(BARK, 1f, 0.95f + getRandom().nextFloat() * 0.15f);
		}
		if (!counterOpen && waitTicks % 200 == 0) {
			askForFee(target);
		}
		if (waitTicks > PAY_WAIT_TICKS) {
			giveUp(level, target, "gave_up");
		}
	}

	private void askForFee(ServerPlayer target) {
		String key = fee == 0 ? "message.spacebod_observer.pawstman.free" : fee == 1 ? "message.spacebod_observer.pawstman.fee_one" : "message.spacebod_observer.pawstman.fee";
		target.sendSystemMessage(Component.translatable(key, fee).withStyle(ChatFormatting.GOLD), true);
	}

	/** Its reader opens its little counter to pay or turn it away. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (awaitingPayment() && player.getUUID().equals(recipient)) {
			if (!level().isClientSide()) {
				counterOpen = true;
				getNavigation().stop();
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new PawstmanMenu(id, inventory, this),
						Component.translatable("entity.spacebod_observer.delivery_dog")));
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	int fee() {
		return fee;
	}

	int issue() {
		return issue;
	}

	/** Whether {@code player} may still use its counter: its reader, nearby, while it waits. */
	boolean waitingFor(Player player) {
		return awaitingPayment() && isAlive() && player.getUUID().equals(recipient) && player.distanceToSqr(this) < 8 * 8;
	}

	/** Paid in full at the counter: the paper changes paws. */
	void paid(ServerPlayer reader) {
		if (awaitingPayment()) {
			paid = fee;
			playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
			handOver(reader);
		}
	}

	/** Turned away at the counter. */
	void declined(ServerPlayer reader) {
		if (awaitingPayment() && level() instanceof ServerLevel level) {
			giveUp(level, reader, "declined");
		}
	}

	void counterClosed() {
		counterOpen = false;
	}

	/** Unpaid: takes the paper home; the reader buys their copy at a newsstand instead. */
	private void giveUp(ServerLevel level, ServerPlayer target, String why) {
		gaveUp = true;
		setCustomNameVisible(false);
		Newsroom.markOffered(target);
		target.sendSystemMessage(Component.translatable("message.spacebod_observer.pawstman." + why).withStyle(ChatFormatting.GRAY));
		playSound(wolfSound("whine"), 1f, 1f);
		leave();
	}

	private void handOver(ServerPlayer target) {
		BehaviorUtils.throwItem(this, paper, target.position().add(0, 0.3, 0));
		setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		playSound(BARK, 1f, 1.1f);
		playSound(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1f);
		playSound(SoundEvents.VILLAGER_YES, 0.5f, 1.3f);
		if (level() instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.4, target.getZ(), 12, 0.4, 0.3, 0.4, 0.0);
		}
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

	/** Left without delivering, unpaid: the delivery is over and nothing is owed. */
	boolean gaveUp() {
		return gaveUp;
	}

	/** Waiting at the reader's heels for its fee; the delivery's own timeout doesn't apply. */
	boolean awaitingPayment() {
		return awaitingPayment && !delivered && !gaveUp;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!delivered && !gaveUp && recipient != null && level.getEntity(recipient) instanceof ServerPlayer target) {
			if (source.getEntity() == target) {
				// Turned away: it keeps the paper, unhurt, and goes; the copy stays at the newsstands.
				giveUp(level, target, "shooed");
			}
			// On its round nothing else hurts it: falls, water and the rest don't end a delivery.
			return false;
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
