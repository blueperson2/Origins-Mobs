package com.starshootercity.abilities;

import com.starshootercity.OriginSwapper;
import com.starshootercity.OriginsMobs;
import com.starshootercity.events.PlayerLeftClickEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kyori.adventure.key.Key;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TridentExpert implements VisibleAbility, Listener, AttributeModifierAbility {
    @Override
    public @NotNull List<OriginSwapper.LineData.LineComponent> getDescription() {
        return OriginSwapper.LineData.makeLineFor("You're a master of the trident, dealing +2 damage when you throw it, and +2 melee damage with it. You can also use channeling without thunder, and use riptide without rain/water at the price of extra durability.", OriginSwapper.LineData.LineComponent.LineType.DESCRIPTION);
    }

    @Override
    public @NotNull List<OriginSwapper.LineData.LineComponent> getTitle() {
        return OriginSwapper.LineData.makeLineFor("Trident Expert", OriginSwapper.LineData.LineComponent.LineType.TITLE);
    }

    @Override
    public @NotNull Key getKey() {
        return Key.key("moborigins:trident_expert");
    }

    private final NamespacedKey riptideKey = new NamespacedKey(OriginsMobs.getInstance(), "riptide-trident");

    public ItemStack fixTrident(ItemStack item) {
        if (item.getItemMeta().getPersistentDataContainer().has(riptideKey)) {
            ItemMeta meta = item.getItemMeta();
            int level = item.getItemMeta().getPersistentDataContainer().getOrDefault(riptideKey, PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().remove(riptideKey);
            meta.addEnchant(Enchantment.RIPTIDE, level, false);
            meta.removeEnchant(Enchantment.FROST_WALKER);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerStopUsingItem(PlayerStopUsingItemEvent event) {
        if (event.getItem().getType() != Material.TRIDENT) return;
        if (event.getTicksHeldFor() >= 10 && event.getItem().getItemMeta().getPersistentDataContainer().has(riptideKey)) {
            Level level = ((CraftWorld) event.getPlayer().getWorld()).getHandle();
            releaseUsing(CraftItemStack.asNMSCopy(fixTrident(event.getItem())), level, ((CraftLivingEntity) event.getPlayer()).getHandle());
            event.getItem().damage(10, event.getPlayer());
        } else fixTrident(event.getItem());
    }

    @EventHandler
    public void onPlayerLeave(PlayerJoinEvent event) {
        fixTrident(event.getPlayer().getInventory().getItemInMainHand());
        fixTrident(event.getPlayer().getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getPreviousSlot());
        if (item != null && item.getType() == Material.TRIDENT) fixTrident(item);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getPlayer().isInWaterOrRainOrBubbleColumn()) return;
        if (Bukkit.getCurrentTick() - lastTridentEnabledTime.getOrDefault(event.getPlayer(), Bukkit.getCurrentTick() - 400) >= 400) return;
        if (event.getItem() == null || event.getItem().getType() != Material.TRIDENT) return;
        if (!event.getItem().getItemMeta().hasEnchant(Enchantment.RIPTIDE)) return;
        ItemMeta meta = event.getItem().getItemMeta();
        event.getItem().setItemMeta(meta);
        meta.getPersistentDataContainer().set(riptideKey, PersistentDataType.INTEGER, meta.getEnchantLevel(Enchantment.RIPTIDE));
        meta.removeEnchant(Enchantment.RIPTIDE);
        if (meta.getEnchants().isEmpty()) {
            meta.addEnchant(Enchantment.FROST_WALKER, 1, true);
        }
        event.getItem().setItemMeta(meta);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        event.getItemDrop().setItemStack(fixTrident(event.getItemDrop().getItemStack()));
    }

    private final Map<Player, Integer> lastTridentEnabledTime = new HashMap<>();

    @EventHandler
    public void onPlayerLeftClick(PlayerLeftClickEvent event) {
        if (List.of(Material.AIR, Material.TRIDENT).contains(event.getPlayer().getInventory().getItemInMainHand().getType())) {
            AbilityRegister.runForAbility(event.getPlayer(), getKey(), () -> {
                if (Bukkit.getCurrentTick() - lastTridentEnabledTime.getOrDefault(event.getPlayer(), Bukkit.getCurrentTick() - 1200) < 1200) return;
                lastTridentEnabledTime.put(event.getPlayer(), Bukkit.getCurrentTick());
            });
        }
    }

    @Override
    public @NotNull Attribute getAttribute() {
        return Attribute.GENERIC_ATTACK_DAMAGE;
    }

    @Override
    public double getAmount() {
        return 0;
    }

    @Override
    public double getChangedAmount(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TRIDENT && Bukkit.getCurrentTick() - lastTridentEnabledTime.getOrDefault(player, Bukkit.getCurrentTick() - 400) < 400 ? 2 : 0;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            if (event.getEntity() instanceof Trident trident) {
                AbilityRegister.runForAbility(player, getKey(), () -> {
                    if (Bukkit.getCurrentTick() - lastTridentEnabledTime.getOrDefault(player, Bukkit.getCurrentTick() - 400) < 400) {
                        trident.setDamage(trident.getDamage() + 2);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() == null) return;
        if (event.getEntity().getShooter() instanceof Player player && event.getEntity() instanceof Trident trident) {
            AbilityRegister.runForAbility(player, getKey(), () -> {
                if (Bukkit.getCurrentTick() - lastTridentEnabledTime.getOrDefault(player, Bukkit.getCurrentTick() - 400) < 400) {
                    if (trident.getItem().getItemMeta().hasEnchant(Enchantment.CHANNELING)) {
                        event.getHitEntity().getWorld().strikeLightning(event.getHitEntity().getLocation());
                    }
                }
            });
        }
    }

    @Override
    public AttributeModifier.@NotNull Operation getOperation() {
        return AttributeModifier.Operation.ADD_NUMBER;
    }

    public void releaseUsing(net.minecraft.world.item.ItemStack stack, Level world, LivingEntity user) {
        if (user instanceof net.minecraft.world.entity.player.Player entityhuman) {
            int k = EnchantmentHelper.getRiptide(stack);

            if (k > 0) {

                PlayerRiptideEvent event = new PlayerRiptideEvent((Player) entityhuman.getBukkitEntity(), CraftItemStack.asCraftMirror(stack));
                event.getPlayer().getServer().getPluginManager().callEvent(event);
                float f = entityhuman.getYRot();
                float f1 = entityhuman.getXRot();
                float f2 = -Mth.sin(f * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                float f3 = -Mth.sin(f1 * 0.017453292F);
                float f4 = Mth.cos(f * 0.017453292F) * Mth.cos(f1 * 0.017453292F);
                float f5 = Mth.sqrt(f2 * f2 + f3 * f3 + f4 * f4);
                float f6 = 3.0F * ((1.0F + (float) k) / 4.0F);

                f2 *= f6 / f5;
                f3 *= f6 / f5;
                f4 *= f6 / f5;
                if (entityhuman.onGround()) {
                    entityhuman.move(MoverType.SELF, new Vec3(0.0D, 1.1999999284744263D, 0.0D));
                }
                entityhuman.getBukkitEntity().setVelocity(entityhuman.getBukkitEntity().getVelocity().add(new Vector(f2, f3, f4)));
                entityhuman.startAutoSpinAttack(20);

                SoundEvent soundeffect;

                if (k >= 3) {
                    soundeffect = SoundEvents.TRIDENT_RIPTIDE_3;
                } else if (k == 2) {
                    soundeffect = SoundEvents.TRIDENT_RIPTIDE_2;
                } else {
                    soundeffect = SoundEvents.TRIDENT_RIPTIDE_1;
                }

                world.playSound(null, entityhuman, soundeffect, SoundSource.PLAYERS, 1.0F, 1.0F);

            }
        }
    }

}
