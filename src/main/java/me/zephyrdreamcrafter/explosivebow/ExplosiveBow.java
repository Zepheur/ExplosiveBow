package me.zephyrdreamcrafter.explosivebow;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;

public final class ExplosiveBow extends JavaPlugin implements Listener{

    /*
    TODO
        Teams
     */

    // CONFIG DEFAULTS
    FileConfiguration config = getConfig();
    double
            /*
            These values are all used in the linear function:
            Damage = B - M * Distance

            B is the base damage value
            M is the falloff multiplier
            Distance is the distance from the explosion. This is basically never less than 0.4
             */
            baseAIDamage = 35,
            basePlayerDamage = 10,
            baseSelfDamage = 9,
            selfDamageFalloffMultiplier = 0.5,

    falloffMultiplier = 3; // Higher values decrease damage more at the edges of the explosion

    /* baseSelfLaunch power is used in the exponential function
        Velocity = baseSelfLaunchPower / (Distance + 1)
        Which is added on top of the initial launch power from the explosion
     */
    double baseSelfLaunchPower = 1;

    double explosionSize = 4;

    boolean configFire = false;
    boolean configBlockDamage = false;

    boolean configFriendlyFire = true;



    HashMap<Location, Player> explosions = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        System.out.println("Enabled ExplosiveBow by Zephyr F. Dreamcrafter");
        config.options().copyDefaults(true);
        saveConfig();

        baseAIDamage = config.getDouble("baseAIDamage");
        basePlayerDamage = config.getDouble("basePlayerDamage");
        baseSelfDamage = config.getDouble("baseSelfDamage");
        selfDamageFalloffMultiplier = config.getDouble("selfDamageFalloffMultiplier");
        baseSelfLaunchPower = config.getDouble("baseSelfLaunchPower");
        explosionSize = config.getDouble("explosionSize");
        configFire = config.getBoolean("configFire");
        configBlockDamage = config.getBoolean("configBlockDamage");
        configFriendlyFire = config.getBoolean("configFriendlyFire");


        getServer().getPluginManager().registerEvents(this, this);
    }


    @EventHandler
    // Assigning metadata (explosive: true) to an arrow shot from a bow with "explosivebow" in the lore
    public void onexplosiveshoot(EntityShootBowEvent e) {
        ItemStack bow = e.getBow();
        if (bow.hasItemMeta() && bow.getItemMeta().hasLore()) {
            List<String> lore = bow.getItemMeta().getLore();
            boolean isExplosive = false;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("explosivebow")) {
                    isExplosive = true;
                    break;
                }
            }
            if (isExplosive){
                e.getProjectile().setMetadata("explosive", new FixedMetadataValue(this, true));
            }
        }
    }

    @EventHandler
    // Check if an arrow was shot from an explosive bow, and if so, create an explosion
    public void onexplosivehit(ProjectileHitEvent e) {

        if(e.getEntity() instanceof Arrow) {

            Arrow a = (Arrow) e.getEntity();
            boolean explosive = false;

            for (MetadataValue meta : a.getMetadata("explosive")) {
                if (meta.asBoolean()) {
                    explosive = true;
                    a.setMetadata("explosive", new FixedMetadataValue(this, false));
                    break;
                }
            }

            if (explosive) {
                Location loc = a.getLocation();
                World world = loc.getWorld();
                double x = loc.getX();
                double y = loc.getY();
                double z = loc.getZ();

                if (e.getEntity().getShooter() instanceof Player) {
                    Player p = (Player) e.getEntity().getShooter();
                    explosions.put(loc, p);
                }

                world.createExplosion(loc,
                        (float)(explosionSize), // size
                        configFire, // fire
                        configBlockDamage, // block damage
                        a
                );
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getCause().equals(EntityDamageByEntityEvent.DamageCause.ENTITY_EXPLOSION)) {
            Location loc = e.getDamager().getLocation();
            if (explosions.containsKey(loc)) {
                if (e.getEntity() instanceof Player) { // When it hits a player
                    Player p = (Player) e.getEntity();
                    if (explosions.get(loc).getDisplayName().equals(p.getDisplayName())) { // If it's the shooter
                        // Low damage high recoil
                        Location pLoc = p.getLocation();
                        if (e.getDamage() != 1) { // No wallhacks
                            e.setDamage(SelfDamage(loc, pLoc)); // Custom self damage
                        }
                        Vector v = p.getVelocity();
                        p.setVelocity(NewVelocity(v, loc, pLoc)); // Custom self knockback
                    } else {
                        if (onEnemyteam(p, explosions.get(loc).getDisplayName())) {
                            if (e.getDamage() != 1) { // No wallhacks
                                Location peLoc = e.getEntity().getLocation();
                                e.setDamage(PlayerDamage(loc, peLoc)); // Custom player damage
                            }
                        }
                    }
                } else { // AI damage
                    if (e.getDamage() != 1) { // No wallhacks
                        Location aiLoc = e.getEntity().getLocation();
                        e.setDamage(AIDamage(e.getDamage(), loc, aiLoc)); // Custom AI damage
                    }
                }
            }
        }
    }

    /*
    Helper for onEntityDamageByEntity
    Checks if the attempted damage is being dealt to someone on the enemy team
    playerShot == player that was shot
    shooter == name of the player that shot the arrow that is now exploding and attempting to damage shotPlayer
     */
    private boolean onEnemyteam(Player playerShot, String shooter) {
        if (configFriendlyFire) {
            return true;
        }
        return true; // Impliment teamchecking here
    }

    /*
    Helper for onEntityDamageByEntity
    Calculates inflicted self damage by calculating distance
    dLoc == damage location
    pLoc == player location
    Return damage to be done as a double
     */
    private double SelfDamage(Location dLoc, Location pLoc) {
        double d = GetDistance(dLoc, pLoc);
        return baseSelfDamage - (falloffMultiplier * d);
    }

    /*
    Helper for onEntityDamageByEntity
    Calculates inflicted (non self) player damage by calculating distance
    dLoc == damage location
    pLoc == player location
    Return damage to be done as a double
     */
    private double PlayerDamage(Location dLoc, Location pLoc) {
        double d = GetDistance(dLoc, pLoc);
        return basePlayerDamage - (falloffMultiplier * d);
    }

    /*
    Helper for onEntityDamage
    Calculates inflicted AI damage by calculating distance
    dLoc == damage location
    pLoc == AI location
    Return damage to be done as a double
     */
    private double AIDamage(double damage, Location dLoc, Location pLoc) {
        double d = GetDistance(dLoc, pLoc);
        double finalDamage = baseAIDamage - (falloffMultiplier * d);
        return finalDamage;
    }

    /*
    Helper for onEntityDamageByEntity
    Return velocity to add to vector based on selfLaunchPower and distance
    v == current velocity
    aLoc == launching entity location
    bLoc == location of blast
     */
    private Vector NewVelocity(Vector v, Location blastLoc, Location entityLoc) {
        double mult = 0.1; // Overall multiplier. Larger == More powerful. No larger than 1
        double d = GetDistance(entityLoc, blastLoc);
        if (d < 1) d = 1;
        v.setX(v.getX() + (baseSelfLaunchPower / GetDistanceX(entityLoc, blastLoc)) * mult);
        v.setY(v.getY() + (baseSelfLaunchPower / GetDistanceY(entityLoc, blastLoc)) * mult);
        v.setZ(v.getZ() + (baseSelfLaunchPower / GetDistanceZ(entityLoc, blastLoc)) * mult);
        return v;
    }

    /*
    Calculates X distance and direction
    aLoc == first location
    bLoc == second location
    returns a positive or negative double
     */
    private double GetDistanceX(Location aLoc, Location bLoc) {
        double
                x1 = aLoc.getX(),
                x2 = bLoc.getX();
        return GetDistance_Helper(x1, x2);
    }

    /*
    Calculates Y distance and direction
    aLoc == first location
    bLoc == second location
    returns a positive or negative double
     */
    private double GetDistanceY(Location aLoc, Location bLoc) {
        double
                y1 = aLoc.getY(),
                y2 = bLoc.getY();
        return GetDistance_Helper(y1, y2);
    }

    /*
    Calculates Z distance and direction
    aLoc == first location
    bLoc == second location
    returns a positive or negative double
     */
    private double GetDistanceZ(Location aLoc, Location bLoc) {
        double
                z1 = aLoc.getZ(),
                z2 = bLoc.getZ();
        return GetDistance_Helper(z1, z2);
    }

    /*
    Calculates distance and direction
    x == first pos
    y == second pos
    returns a positive or negative double
     */
    private double GetDistance_Helper(double x, double y) {
        double r = Math.abs(x - y);
        r += 1; // Prevent divide by 0 or numbers less than 1
        if (y < x) { // Positive direction
            return r;
        } else { // Negative direction
            return r * -1;
        }
    }

    /*
    Calculates distance
    aLoc == first location
    bLoc == second location
     */
    private double GetDistance(Location aLoc, Location bLoc) {
        double
                ax = aLoc.getX(),
                ay = aLoc.getY(),
                az = aLoc.getZ(),
                bx = bLoc.getX(),
                by = bLoc.getY(),
                bz = bLoc.getZ();


        double
                distanceX = Math.abs(ax - bx),
                distanceY = Math.abs(ay - by),
                distanceZ = Math.abs(az - bz);

        return VectorAddition(distanceX, distanceY, distanceZ);
    }

    private double VectorAddition(double x, double y, double z) {
        double
                xz = Math.sqrt(x * x + z * z),
                xyz = Math.sqrt(xz * xz + y * y);
        return xyz;
    }
}
