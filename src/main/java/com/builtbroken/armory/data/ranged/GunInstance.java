package com.builtbroken.armory.data.ranged;

import com.builtbroken.armory.Armory;
import com.builtbroken.armory.content.entity.projectile.EntityAmmoProjectile;
import com.builtbroken.armory.content.items.ItemGun;
import com.builtbroken.armory.data.ArmoryDataHandler;
import com.builtbroken.armory.data.clip.ClipInstance;
import com.builtbroken.armory.data.clip.ClipInstanceItem;
import com.builtbroken.armory.data.user.IWeaponUser;
import com.builtbroken.mc.api.ISave;
import com.builtbroken.mc.api.abstraction.EffectInstance;
import com.builtbroken.mc.api.abstraction.world.IWorld;
import com.builtbroken.mc.api.data.weapon.*;
import com.builtbroken.mc.api.energy.IEnergyBuffer;
import com.builtbroken.mc.api.energy.IEnergyBufferProvider;
import com.builtbroken.mc.api.items.weapons.IItemAmmo;
import com.builtbroken.mc.api.items.weapons.IItemClip;
import com.builtbroken.mc.api.modules.IModule;
import com.builtbroken.mc.api.modules.weapon.IClip;
import com.builtbroken.mc.api.modules.weapon.IGun;
import com.builtbroken.mc.core.Engine;
import com.builtbroken.mc.core.References;
import com.builtbroken.mc.imp.transform.vector.Location;
import com.builtbroken.mc.imp.transform.vector.Pos;
import com.builtbroken.mc.prefab.inventory.InventoryIterator;
import com.builtbroken.mc.prefab.inventory.InventoryUtility;
import com.builtbroken.mc.prefab.module.AbstractModule;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The actual gun instance used for data accessing and handling
 *
 * @see <a href="https://github.com/BuiltBrokenModding/VoltzEngine/blob/development/license.md">License</a> for what you can and can't do with the code.
 * Created by Dark(DarkGuardsman, Robert) on 11/16/2016.
 */
public class GunInstance extends AbstractModule implements ISave, IGun
{
    public static final String NBT_ROUND = "chamberedRound";
    public static final String NBT_CLIP = "clip";
    /** Enabled ray trace debug on all weapons, normally only client side */
    public static boolean debugRayTraces = false;
    /** Enabled ray trace debug on this weapon only */
    public boolean doDebugRayTracesOnTthisGun = false;


    /** How fast a projectile can travel before a ray trace is used instead */
    public static final float PROJECTILE_SPEED_LIMIT = 100;
    /** Who is holding the weapon */
    public final IWeaponUser weaponUser;
    /** Properties of the weapon */
    protected final IGunData gunData;

    /** Clip that is feed into the weapon */
    public IClip _clip;

    private IAmmoData _chamberedRound;
    public IAmmoData overrideRound;

    /** Last time the weapon was fired, milliseconds */
    public Long lastTimeFired = 0L;
    /** Toggle to do reload */
    public boolean doReload = false;
    /** Is the weapon sighted */
    public boolean isSighted = false;

    /** True = infinite ammo */
    public boolean consumeAmmo = false;
    /** Delay before reloading */
    public int reloadDelay = -1;

    /** Power stored in the gun */
    public int power = 0;

    /** Is the weapon in a lowered, unarmed, state */
    public boolean isLowered = false;
    /** Last time the weapon was taken out of lowered state */
    public long lastTimeUnLowered = 0L;

    public GunInstance(ItemStack gunStack, IWeaponUser weaponUser, IGunData gun)
    {
        super(gunStack, "armory:gun");
        this.weaponUser = weaponUser;
        this.gunData = gun;
        if (isManuallyFeedClip())
        {
            _clip = new ClipInstance(gunData.getBuiltInClipData());
        }
        if (gunStack.getTagCompound() != null)
        {
            load(gunStack.getTagCompound());
        }
    }

    /**
     * Called to fire the weapon
     *
     * @param world      -world fired in
     * @param ticksFired - number of ticks fired for
     */
    public void fireWeapon(World world, int ticksFired)
    {
        fireWeapon(world, ticksFired, null, weaponUser.getEntityAim());
    }

    /**
     * Called to fire the weapon
     *
     * @param world      -world fired in
     * @param ticksFired - number of ticks fired for
     */
    public void fireWeapon(World world, int ticksFired, Pos aimPoint, Pos aim)
    {
        //Check if sighted is required to fire
        if (isSighted || !gunData.isSightedRequiredToFire())
        {
            Long deltaTime = System.currentTimeMillis() - lastTimeFired;
            if (weaponUser != null && (lastTimeFired == 0L || deltaTime > gunData.getFiringDelay()))
            {
                lastTimeFired = System.currentTimeMillis();
                float pitch = (float) weaponUser.pitch();
                float yaw = (float) weaponUser.yaw();

                _doFire(world, yaw, pitch, aimPoint, aim);
            }
        }
        else
        {
            //TODO play some other notation that the weapon should be sighted
            playAudio("error.aim.needed");
        }
    }

    protected void _doFire(World world, float yaw, float pitch, Pos aimPointOverride, Pos aim)
    {
        //TODO add safety checks
        if (getChamberedRound() != null || hasMagWithAmmo())
        {
            //Load next round to fire if empty
            chamberNextRound();

            //Only fire if we have a round in the chamber
            if (getChamberedRound() != null && hasEnergyToFire())
            {
                //TODO apply upgrades

                //Notes for player statistics to add
                //TODO track damage by Weapon and Entity(If player) - do not store in weapon data
                //TODO track shots fired - do not store in weapon data
                //TODO track shots hit - do not store in weapon data
                //TODO track kills - do not store in weapon data
                //TODO allow data to be cleared & disabled
                //TODO allow sorting of the data and graphing
                //TODO allow other users to see each other's weapon data (with a permission system)

                final Pos entityPos = weaponUser.getEntityPosition();
                final Pos bulletStartPoint = entityPos.add(weaponUser.getProjectileSpawnOffset().add(getGunData().getProjectileSpawnOffset()));
                final Pos target = aimPointOverride != null ? aimPointOverride : entityPos.add(aim.multiply(500));

                playAudio("round.fired");
                playEffect("round.fired", bulletStartPoint, aim, aim, false); //TODO check with ammo if it has an effect to play then use this as backup

                //Fire round out of gun
                if (getChamberedRound().getProjectileVelocity() <= 0 || getChamberedRound().getProjectileVelocity() > PROJECTILE_SPEED_LIMIT)
                {
                    _doRayTrace(world, yaw, pitch, getChamberedRound(), entityPos.add(aim), target, aim);
                }
                else
                {
                    _createAndFireEntity(world, yaw, pitch, getChamberedRound(), bulletStartPoint, target, aim);
                }

                List<ItemStack> droppedItems = new ArrayList();
                getChamberedRound().getEjectedItems(droppedItems);
                if (droppedItems != null && droppedItems.size() > 0)
                {
                    //TODO implement ejection collectors
                    Pos ejectionPoint = weaponUser.getProjectileSpawnOffset().add(getGunData().getEjectionSpawnOffset());
                    playAudio("round.eject");
                    playEffect("round.eject", ejectionPoint, getGunData().getEjectionSpawnVector(), aim, false);
                    for (ItemStack stack : droppedItems)
                    {
                        EntityItem item = InventoryUtility.dropItemStack(world, ejectionPoint, stack, 5 + world.rand.nextInt(20), 0.3f);
                        if (item != null)
                        {
                            item.motionX += getGunData().getEjectionSpawnVector().x();
                            item.motionY += getGunData().getEjectionSpawnVector().y();
                            item.motionZ += getGunData().getEjectionSpawnVector().z();
                        }
                    }
                }

                //Clear current round as it has been fired
                consumeShot();

                //TODO generate heat
                //TODO apply recoil
                //TODO damage weapon
                //Load next round to fire
                chamberNextRound();
            }
            weaponUser.updateWeaponStack(this, toStack(), "_doFire");
        }

        if (!hasMagWithAmmo())
        {
            doReload = true;
            playAudio("empty");
        }
    }

    /**
     * Called to chamber next round
     *
     * @return true if chamber has round
     */
    public boolean chamberNextRound()
    {
        if (getChamberedRound() == null)
        {
            if (getChamberedRound() == null && hasMagWithAmmo())
            {
                _chamberedRound = getLoadedClip().getAmmo().peek();
                getLoadedClip().consumeAmmo(1);
                weaponUser.updateWeaponStack(this, toStack(), "chamber round");
                playAudio("round.chamber");
            }
            return getChamberedRound() != null;
        }
        return true;
    }

    /**
     * Called to run debug ray trace code
     * <p>
     * Has to be triggered in order to be used.
     */
    public void debugRayTrace()
    {
        final Pos entityPos = weaponUser.getEntityPosition();
        final Pos aim = weaponUser.getEntityAim();
        final Pos target = entityPos.add(aim.multiply(500));
        debugRayTrace(entityPos, aim, target, weaponUser.getProjectileSpawnOffset());
    }

    /**
     * Called to run debug ray trace code
     * <p>
     * Has to be triggered in order to be used.
     */
    public void debugRayTrace(Pos entityPos, Pos aim, Pos target, Pos bulletOffset)
    {
        if ((debugRayTraces || doDebugRayTracesOnTthisGun) && Engine.loaderInstance != null && Engine.runningAsDev)
        {
            final Pos bulletStartPoint = entityPos.add(bulletOffset);
            final Pos start = entityPos.add(aim);

            Pos end = target;
            MovingObjectPosition hit = start.rayTrace(weaponUser.oldWorld(), end, false, true, false);
            if (hit != null)
            {
                end = new Pos(hit.hitVec);
            }

            IWorld world = Engine.minecraft.getWorld(weaponUser.oldWorld().provider.dimensionId); //TODO remove once world is switch over to wrapper

            //Test ray cast
            EffectInstance effect = world.newEffect(References.LASER_EFFECT, start);
            effect.setEndPoint(end);
            effect.addData("red", (Color.blue.getRed() / 255f));
            effect.addData("green", (Color.blue.getRed() / 255f));
            effect.addData("blue", (Color.blue.getRed() / 255f));
            effect.send();

            //Test bullet ray case
            effect.setPosition(bulletStartPoint);
            effect.setEndPoint(end);
            effect.addData("red", (Color.GREEN.getRed() / 255f));
            effect.addData("green", (Color.GREEN.getRed() / 255f));
            effect.addData("blue", (Color.GREEN.getRed() / 255f));
            effect.send();
        }
    }

    protected void _doRayTrace(World world, float yaw, float pitch, IAmmoData nextRound, Pos start, Pos end, Pos aim)
    {
        MovingObjectPosition hit = start.rayTrace(world, end, false, true, false);
        if (hit != null && hit.typeOfHit != MovingObjectPosition.MovingObjectType.MISS)
        {
            if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
            {
                nextRound.onImpactEntity(weaponUser.getShooter(), hit.entityHit, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord, nextRound.getProjectileVelocity()); //TODO scale velocity by distance
            }
            else
            {
                nextRound.onImpactGround(weaponUser.getShooter(), world, hit.blockX, hit.blockY, hit.blockZ, hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord, nextRound.getProjectileVelocity());
            }
            playEffect("round.fired.rayTrace", start, new Pos(hit.hitVec), aim, true);
        }
        else
        {
            playEffect("round.fired.rayTrace", start, end, aim, true);
        }
    }

    protected void _createAndFireEntity(World world, float yaw, float pitch, IAmmoData nextRound, Pos start, Pos end, Pos aim)
    {
        Pos spawnPoint = weaponUser.getEntityPosition().add(aim).add(weaponUser.getProjectileSpawnOffset());
        //TODO spawn projectile
        EntityAmmoProjectile projectile = new EntityAmmoProjectile(world, nextRound, this, weaponUser.getShooter());

        projectile.setPosition(spawnPoint.x(), spawnPoint.y(), spawnPoint.z());

        //Calculate deltas
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double deltaZ = end.z() - start.z();

        projectile.setThrowableHeading(deltaX, deltaY, deltaZ, nextRound.getProjectileVelocity(), 0);

        world.spawnEntityInWorld(projectile);

    }

    protected void consumeShot()
    {
        if (getChamberedRound() != null)
        {
            if (!consumeAmmo)
            {
                ReloadType reloadType = getGunData().getReloadType();
                if (reloadType.requiresItems())
                {
                    _chamberedRound = null;
                }
                consumeEnergyToFire();
            }
            playAudio("round.consume");
            weaponUser.updateWeaponStack(this, toStack(), "consume ammo");
        }
    }

    /**
     * Called to see if the gun has an ammo item inserted.
     * Does not check chambered round, so will not work
     * for single shot weapons.
     *
     * @return true if has ammo inserted
     */
    public boolean hasMagWithAmmo()
    {
        return getLoadedClip() != null && getLoadedClip().getAmmoCount() > 0;
    }

    /**
     * Checks if the gun still has ammo to fire
     *
     * @return
     */
    public boolean hasAmmo()
    {
        return hasMagWithAmmo() || getChamberedRound() != null;
    }

    public boolean isFullOnAmmo()
    {
        return getLoadedClip() != null && getLoadedClip().getAmmoCount() >= getLoadedClip().getMaxAmmo();
    }

    /**
     * Called to tick the gun for reload.
     */
    public void doReloadTick()
    {
        //If first tick set reload time
        if (reloadDelay == -1)
        {
            //Play reload init
            playAudio("reload");

            //Init reload delay
            reloadDelay = gunData.getReloadTime();
        }
        //Tick reload timer
        if (reloadDelay-- <= 0)
        {
            //Detect if single shot weapon
            boolean singleShot = gunData.getReloadType() == ReloadType.BREACH_LOADED || gunData.getReloadType() == ReloadType.FRONT_LOADED;
            boolean itemDrainReload = !gunData.getReloadType().requiresItems();

            //If not single shot (clip) or single shot & no round, do reload
            if (!singleShot || getChamberedRound() == null)
            {
                //Reload weapon
                if (reloadWeapon(weaponUser.getInventory(), true))
                {
                    //Chamber next round
                    chamberNextRound();
                }
            }
            else if (itemDrainReload)
            {
                //TODO add reload for items
                //TODO if changed remove reload check from ItemGun
                //TODO add extra check to reload to allow guns to turn off reload manual ability (for use with machine charged, external reloaded, or single use items)
            }

            cancelReload();
        }
        //Play animation or audio while reloading
        else
        {
            //TODO run animation
            //TODO randomize audio TODO randomize volume TODO get gun position
            if (reloadDelay % 20 == 0)
            {
                //Play reload tick audio
                playAudio("reload.tick");
            }
        }
    }

    public void cancelReload()
    {
        //End reload
        doReload = false;
        reloadDelay = -1;
    }

    public void playAudio(String key)
    {
        //Checks for JUnit testing TODO fix
        if (weaponUser != null && weaponUser.oldWorld() != null && weaponUser.oldWorld().provider != null)
        {
            int dim = weaponUser.oldWorld().provider.dimensionId;
            IWorld world = Engine.getWorld(dim);
            if (world != null)
            {
                //TODO get weapon position
                world.playAudio(gunData.getUniqueID() + "." + key, weaponUser.x(), weaponUser.y() + 1.1f, weaponUser.z(), 1, 1);
            }
            else if (Engine.runningAsDev)
            {
                Engine.logger().error("GunInstance#playAudio(" + key + ") failed to get world instance for dim[  " + dim + "  ]");
            }
        }
    }

    public void playEffect(String key, Pos pos, Pos end, Pos aim, boolean endPoint)
    {
        //Checks for JUnit testing TODO fix
        if (Engine.loaderInstance != null)
        {
            int dim = weaponUser.oldWorld().provider.dimensionId;
            IWorld world = Engine.getWorld(dim);
            if (world != null)
            {
                EffectInstance effect = world.newEffect(gunData.getUniqueID() + "." + key, pos);
                effect.setMotion(end);
                effect.endPoint = endPoint;

                //Send extra rotation and aim data to client to help translate renders
                effect.addData("aim", aim.toNBT());
                effect.addData("yaw", (float) weaponUser.yaw());
                effect.addData("pitch", (float) weaponUser.pitch());

                //Trigger data to be sent
                effect.send();
            }
            else if (Engine.runningAsDev)
            {
                Engine.logger().error("GunInstance#playEffect(" + key + ",....) failed to get world instance for dim[  " + dim + "  ]");
            }
        }
    }

    /**
     * Called to reload the weapon from inventory
     *
     * @param inventory - inventory of the entity
     */
    public boolean reloadWeapon(IInventory inventory, boolean doAction)
    {
        return reloadWeapon(inventory, doAction, !isManuallyFeedClip());
    }

    /**
     * Called to reload the weapon from inventory
     *
     * @param inventory - inventory of the entity
     */
    public boolean reloadWeapon(IInventory inventory, boolean doAction, boolean unload)
    {
        if (!getGunData().getReloadType().requiresItems())
        {
            return true;
        }
        boolean reloaded = false;
        if (inventory != null)
        {
            if (isManuallyFeedClip())
            {
                reloaded = loadRound(inventory, doAction);
            }
            else
            {
                if (doAction && unload)
                {
                    unloadWeapon(inventory);
                }
                reloaded = loadBestClip(inventory, doAction);
            }
        }
        else if (Armory.INSTANCE != null)
        {
            Armory.INSTANCE.logger().error("Reload was called on '" + weaponUser + "' but it had no inventory.");
        }
        if (doAction)
        {
            //Mark that reload was processed
            doReload = false;
        }
        if (reloaded && doAction)
        {
            weaponUser.updateWeaponStack(this, toStack(), "reload weapon");
        }
        return reloaded;
    }

    private boolean loadBestClip(IInventory inventory, boolean doAction)
    {
        InventoryIterator it = new InventoryIterator(inventory, true);
        IClip bestClip = null;
        int slot = -1;
        for (ItemStack stack : it)
        {
            if (weaponUser.isAmmoSlot(it.slot()))
            {
                if (stack.getItem() instanceof IItemClip)
                {
                    IItemClip itemClip = ((IItemClip) stack.getItem());
                    if (itemClip.isAmmo(stack) && itemClip.isClip(stack))
                    {
                        IClipData clipData = itemClip.getClipData(stack);
                        if (clipData != null)
                        {
                            IAmmoType type = clipData.getAmmoType();
                            if (type == gunData.getAmmoType())
                            {
                                if (bestClip == null || bestClip.getAmmoCount() < itemClip.getAmmoCount(stack))
                                {
                                    IClip clip = itemClip.toClip(stack);
                                    if (clip != null)
                                    {
                                        bestClip = clip;
                                        slot = it.slot();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (bestClip != null)
        {
            if (doAction)
            {
                if (_clip == null)
                {
                    playAudio("clip.load");
                    _clip = bestClip;
                    inventory.decrStackSize(slot, 1);
                }
            }
            return true;
        }
        else if (weaponUser instanceof EntityPlayer)
        {
            ((EntityPlayer) weaponUser).addChatComponentMessage(new ChatComponentText("There is no ammo of type '" + gunData.getAmmoType() + "' to load into the gun.")); //TODO translate
        }
        return false;
    }

    private boolean loadRound(IInventory inventory, boolean doAction)
    {
        int roundsLoad = 0;
        InventoryIterator it = new InventoryIterator(inventory, true);
        for (ItemStack stack : it)
        {
            if (weaponUser.isAmmoSlot(it.slot()))
            {
                if (stack.getItem() instanceof IItemAmmo)
                {
                    IItemAmmo ammo = ((IItemAmmo) stack.getItem());
                    if (ammo.isAmmo(stack) && !ammo.isClip(stack))
                    {
                        IAmmoData data = ammo.getAmmoData(stack);
                        IAmmoType type = data.getAmmoType();
                        if (type == gunData.getAmmoType())
                        {
                            if (getLoadedClip().getAmmoCount() < getLoadedClip().getClipData().getMaxAmmo())
                            {
                                //Decrease stack and load gun
                                if (doAction)
                                {
                                    int l = getLoadedClip().loadAmmo(data, ammo.getAmmoCount(stack));
                                    inventory.decrStackSize(it.slot(), l);
                                    roundsLoad += l;
                                }
                                else
                                {
                                    roundsLoad += 1;
                                }
                            }
                        }
                    }
                }

                //Exit loop if full
                if (getLoadedClip().getAmmoCount() >= getLoadedClip().getMaxAmmo())
                {
                    break;
                }
            }
        }
        if (roundsLoad > 0)
        {
            playAudio("round.load");
            return true;
        }
        else if (weaponUser instanceof EntityPlayer)
        {
            ((EntityPlayer) weaponUser).addChatComponentMessage(new ChatComponentText("There is no ammo of type '" + gunData.getAmmoType() + "' to load into the gun."));  //TODO translate
        }
        return false;
    }

    public void unloadWeapon(IInventory inventory)
    {
        if (_clip != null)
        {
            playAudio("unload");
            if (isManuallyFeedClip())
            {
                int i = 0;
                while (!getLoadedClip().getAmmo().isEmpty())
                {
                    ItemStack ammoStack = getLoadedClip().getAmmo().peek().toStack();
                    for (; i < inventory.getSizeInventory(); i++)
                    {
                        if (weaponUser.isAmmoSlot(i))
                        {
                            ItemStack slotStack = inventory.getStackInSlot(i);
                            int roomLeft = InventoryUtility.roomLeftInSlot(inventory, i);
                            if (slotStack == null)
                            {
                                inventory.setInventorySlotContents(i, ammoStack);
                                getLoadedClip().consumeAmmo(1);
                                ammoStack = null;
                                break;
                            }
                            else if (roomLeft > 0 && InventoryUtility.stacksMatch(ammoStack, slotStack))
                            {
                                int insert = Math.min(roomLeft, ammoStack.stackSize);
                                slotStack.stackSize += insert;
                                ammoStack.stackSize -= insert;
                                inventory.setInventorySlotContents(i, slotStack);
                                getLoadedClip().consumeAmmo(1);
                                if (ammoStack.stackSize <= 0)
                                {
                                    ammoStack = null;
                                    break;
                                }
                            }
                        }
                        //No space to insert so drop
                        if (ammoStack != null && ammoStack.stackSize >= 0)
                        {
                            InventoryUtility.dropItemStack(new Location(weaponUser), ammoStack);
                        }
                    }
                }
            }
            else if (_clip instanceof IModule)
            {
                //TODO send to inventory first, not hotbar
                ItemStack stack = ((IModule) _clip).toStack();
                for (int i = 0; i < inventory.getSizeInventory(); i++)
                {
                    if (weaponUser.isAmmoSlot(i))
                    {
                        ItemStack slotStack = inventory.getStackInSlot(i);
                        int roomLeft = InventoryUtility.roomLeftInSlot(inventory, i);
                        if (slotStack == null)
                        {
                            inventory.setInventorySlotContents(i, stack);
                            stack = null;
                            break;
                        }
                        else if (roomLeft > 0 && InventoryUtility.stacksMatch(stack, slotStack))
                        {
                            int insert = Math.min(roomLeft, stack.stackSize);
                            slotStack.stackSize += insert;
                            stack.stackSize -= insert;
                            inventory.setInventorySlotContents(i, slotStack);
                            break;
                        }
                    }
                }
                //No space to insert so drop
                if (stack != null && stack.stackSize >= 0)
                {
                    InventoryUtility.dropItemStack(new Location(weaponUser), stack);
                }
                _clip = null;
            }
            weaponUser.updateWeaponStack(this, toStack(), "unloadWeapon");
        }
    }

    public boolean isManuallyFeedClip()
    {
        return getGunData().getReloadType() == ReloadType.BREACH_LOADED
                || getGunData().getReloadType() == ReloadType.FRONT_LOADED
                || getGunData().getReloadType() == ReloadType.HAND_FEED;
    }

    public boolean hasSights()
    {
        //TODO implement
        return false;
    }

    public void sightWeapon()
    {
        isSighted = !isSighted;
    }

    public void load(NBTTagCompound tag)
    {
        if (tag.hasKey(NBT_ROUND))
        {
            _chamberedRound = (IAmmoData) ArmoryDataHandler.INSTANCE.get("ammo").get(tag.getString(NBT_ROUND));
            if (_chamberedRound == null)
            {
                error("Failed to load chambered round '" + tag.getString(NBT_ROUND) + "' form NBT for " + this);
            }
        }
        if (tag.hasKey(NBT_CLIP))
        {
            NBTTagCompound clipTag = tag.getCompoundTag(NBT_CLIP);
            //Contains data its a clip item
            if (clipTag.hasKey("data"))
            {
                IClipData data = (IClipData) ArmoryDataHandler.INSTANCE.get("clip").get(clipTag.getString("data"));
                //TODO ensure clip data is valid for weapon in case unique ID changes or data changes
                if (data != null)
                {
                    if (clipTag.hasKey("stack"))
                    {
                        //External clip loading
                        ItemStack stack = ItemStack.loadItemStackFromNBT(clipTag.getCompoundTag("stack"));
                        _clip = new ClipInstanceItem(stack, data);
                    }
                }
                else
                {
                    error("Failed to load clip data from NBT using unique id '" + clipTag.getString("data") + "' while loading " + this);
                }
            }
            //Else internal clip
            else
            {
                ((ClipInstance) _clip).load(clipTag);
            }
        }
        power = tag.getInteger(ItemGun.NBT_ENERGY);
    }

    @Override
    protected boolean saveTag()
    {
        return false;
    }

    @Override
    public NBTTagCompound save(NBTTagCompound nbt)
    {
        if (getChamberedRound() != null)
        {
            nbt.setString(NBT_ROUND, getChamberedRound().getUniqueID());
        }
        if (_clip != null)
        {
            NBTTagCompound clipTag = new NBTTagCompound();
            if (_clip instanceof ClipInstance)
            {
                if (_clip.getAmmoCount() > 0)
                {
                    ((ClipInstance) _clip).save(clipTag);
                    nbt.setTag(NBT_CLIP, clipTag);
                }
            }
            else
            {
                clipTag.setString("data", _clip.getClipData().getUniqueID());
                clipTag.setTag("stack", ((ClipInstanceItem) _clip).toStack().writeToNBT(new NBTTagCompound()));
                nbt.setTag(NBT_CLIP, clipTag);
            }
        }
        //Only save power if not zero & we can use power
        if (power != 0 && gunData.getBufferData() != null)
        {
            nbt.setInteger(ItemGun.NBT_ENERGY, power);
        }
        return nbt;
    }

    private void error(String msg)
    {
        if (Armory.INSTANCE != null)
        {
            Armory.INSTANCE.logger().error(msg);
        }
        else
        {
            throw new RuntimeException(msg);
        }
    }

    @Override
    public IGunData getGunData()
    {
        return gunData;
    }

    @Override
    public IAmmoData getChamberedRound()
    {
        if (overrideRound != null)
        {
            return overrideRound;
        }
        if (getGunData().getOverrideAmmo() != null)
        {
            return getGunData().getOverrideAmmo();
        }
        return _chamberedRound;
    }

    /**
     * Called to check if enough energy exists to fire weapons
     *
     * @return true if enough energy exists to fire
     */
    protected boolean hasEnergyToFire()
    {
        return getChamberedRound() != null && consumeEnergy(getChamberedRound().getEnergyCost(), false);
    }

    /**
     * Called to consume energy to fire the weapon
     *
     * @return true if energy was consumed for the operation
     */
    protected boolean consumeEnergyToFire()
    {
        return getChamberedRound() != null && consumeEnergy(getChamberedRound().getEnergyCost(), true);
    }

    /**
     * Called to consume energy
     *
     * @param energyCost - cost of the operation
     * @param doAction   - true to do action, false to test logic
     * @return true if enough energy has been consumed
     */
    protected boolean consumeEnergy(int energyCost, boolean doAction)
    {
        int energyNeeded = energyCost;
        if (energyCost > 0)
        {
            //Consume internal power
            energyNeeded -= power;
            if (doAction)
            {
                power = Math.max(0, power - energyCost);
            }

            //Consume external power
            if (energyNeeded > 0 && weaponUser instanceof IEnergyBufferProvider) //TODO add checks if the gun allows this logic or if special gear is needed
            {
                IEnergyBuffer buffer = ((IEnergyBufferProvider) weaponUser).getEnergyBuffer(ForgeDirection.UNKNOWN);
                if (buffer != null)
                {
                    energyNeeded -= buffer.removeEnergyFromStorage(energyNeeded, doAction);
                }
            }
            //if energy needed is zero we consumed enough for the operation
            return energyNeeded <= 0;
        }
        return true;
    }

    @Override
    public IClip getLoadedClip()
    {
        return _clip;
    }

    @Override
    public String getSaveID()
    {
        return "armory:gun";
    }

    @Override
    public String toString()
    {
        return "GunInstance[" + weaponUser + ", " + gunData + ", " + getChamberedRound() + ", " + _clip + "]";
    }
}
