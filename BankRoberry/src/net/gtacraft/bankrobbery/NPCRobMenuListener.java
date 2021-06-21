package net.gtacraft.bankrobbery;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;
import me.Strobe.gtacore.Runnables.CopSpawnRunnable;
import me.Strobe.gtacore.Utilities.Gangs.Clan;
import me.Strobe.gtacore.Utilities.Wanted.WPlayer;
import me.Strobe.gtacore.Utilities.Wanted.WantedUtils;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import static me.Strobe.gtacore.Core.*;
import static me.Strobe.gtacore.Utilities.CoreUtils.colorize;

public class NPCRobMenuListener implements Listener {
	private final Random r = new Random();
	public static final Map<UUID, RobStorageObject> robs = new HashMap<UUID, RobStorageObject>();

	static int afterRoberryID,beginRobID,halfDoneID;
	public static String formatMilliseconds(long timeInMilliSeconds) {
		long seconds = timeInMilliSeconds / 1000;
		long minutes = seconds / 60;
		String time =
				Utils.translate(("§e§l" + minutes % 60 + "§em §l" + seconds % 60 +
						"§es..."));
		return time;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		// make sure we're in the rob menu inventory
		boolean compareInventories = e.getInventory().getTitle().equals(NPCRobMenu.MENU_TITLE);

		if (!(e.getWhoClicked() instanceof Player) || !compareInventories) {
			return;
		}

		e.setCancelled(true);

		final Player p = (Player) e.getWhoClicked();
		WPlayer wP = WPlayer.getByPlayer(p);

		// rob slot clicked
		if (e.getSlot() == 4) {
			final int id = Integer.parseInt(ChatColor.stripColor(e.getInventory().getItem(4).getItemMeta().getLore().get(7)));
			final ConfigurationSection section = NPCConfigHelper.getNPCSection(id);
			final int timeToRob = section.getInt("time-to-rob");
			final NPC npc = CitizensAPI.getNPCRegistry().getById(id);
			final Location spawnLocation = npc.getStoredLocation();

			BukkitRunnable halfDone = new BukkitRunnable(){
				@Override
				public void run(){

					halfDoneID = this.getTaskId();
					p.sendMessage(Utils.translate("&a&l&n50%&a&l of the robbery is done!"));
					p.sendMessage(Utils.translate("&7Stay inside the store to finish robbing it."));

					Location spawn = new Location(
							Bukkit.getWorld(NPCConfigHelper.getNPCSection(id).getString("pigmen.world")),
							NPCConfigHelper.getNPCSection(id).getDouble("pigmen.x"),
							NPCConfigHelper.getNPCSection(id).getDouble("pigmen.y"),
							NPCConfigHelper.getNPCSection(id).getDouble("pigmen.z"));

					for (int i = 0; i < 2; i++) {
						PigZombie pz = p.getLocation().getWorld().spawn(spawn, PigZombie.class);
						pz.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1));
						pz.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE, 1));
						pz.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS, 1));
						pz.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS, 1));
						pz.setCanPickupItems(false);
						pz.setCustomName(Utils.translate("&b&lCop"));
						pz.setCustomNameVisible(true);
						ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
						sword.addEnchantment(Enchantment.DAMAGE_ALL, 5);
						sword.addEnchantment(Enchantment.FIRE_ASPECT, 2);
						pz.getEquipment().setItemInHand(new ItemStack(sword));
						pz.setTarget(p);
					}
				}
			};
			BukkitRunnable afterRobbery = new BukkitRunnable(){
				@Override
				public void run(){
					afterRoberryID = this.getTaskId();
					Bukkit.getScheduler().cancelTask(beginRobID);
					//BarAPI.removeBar(p);
					halfDone.cancel();

					Main.ctp.getTagManager().untag(p.getUniqueId());
					Map<Integer, String> itemsGained = new HashMap<Integer, String>();

					// give loot to player
					for (String uuid : section.getConfigurationSection("loot.items").getKeys(false)) {
						int next = r.nextInt(100) + 1;

						if (next <= Integer.parseInt(section.getString("loot.items." + uuid + ".chance").trim())) {
							ItemStack i = section.getItemStack("loot.items." + uuid + ".item");
							i.setAmount(section.getInt("loot.items." + uuid + ".amount"));


							String name = i.getItemMeta().hasDisplayName() ? i.getItemMeta().getDisplayName()
									: i.getType().toString().replace("_", " ");
							itemsGained.put(i.getAmount(), name);
							p.getInventory().addItem(i);
						}
					}

					for (String uuid : section.getConfigurationSection("loot.commands").getKeys(false)) {
						int next = r.nextInt(100) + 1;

						if (next <= Integer.parseInt(section.getString("loot.commands." + uuid + ".chance").trim())) {
							Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
									section.getConfigurationSection("loot.commands." + uuid).getString("command")
											.replaceAll("%PLAYER%", p.getName()));
						}
					}

					// successfully robbed store

					if (itemsGained.isEmpty()) {
						p.sendMessage(Utils.translate("&c&lSTORE ROBBERY UNSUCCESSFUL"));
						p.sendMessage(Utils.translate("&7The store clerk was able to escape."));
						p.sendMessage(Utils.translate("&7No items received."));
						p.sendMessage(Utils.translate("&6Your wanted level has increased... Start running!"));
						wP.addWantedLevel(r.nextInt(5)+ 2);
					} else {
						p.sendMessage(Utils.translate("&a&lROBBED STORE SUCCESSFULLY"));
						p.sendMessage(Utils.translate("&7You have successfully robbed this store"));
						p.sendMessage(Utils.translate("&7and collected..."));

						for (Entry<Integer, String> lootItemEntry : itemsGained.entrySet()) {
							p.sendMessage(
									Utils.translate("&a&l* x" + lootItemEntry.getKey() + " " + lootItemEntry.getValue()));
						}
						p.sendMessage(Utils.translate("&6Your wanted level has increased... Start running!"));
						wP.addWantedLevel(r.nextInt(5)+ 2);

						//utils.addStat(p.getUniqueId().toString(), CHALLENGE.STORES_ROBBED);
						Clan clan = Clan.getClanByPlayerUUID(p.getUniqueId());
						if(clan != null) {
							clan.setGangPoints(clan.getGangPoints() + 1);
						}
					}
					if(!bukkitScheduler.isCurrentlyRunning(wP.getCopChasingID()) && wP.getWantedLevel() >= 5){
						CopSpawnRunnable x = new CopSpawnRunnable(wP, 6000L);
						x.runTaskTimer(CORE, 0L, 6000L);
						wP.setCopChasingID(x.getTaskId());
						if(wP.isAlerted())
							p.sendMessage(colorize(GTAWANTEDTAG + "&c&lALERT! &7Because of your &CWanted Level&7, you will now be chased down until you die!"));
						p.sendMessage(colorize(GTACOPTAG + "We found you, &owe're coming for you..."));
					}

					p.playSound(p.getLocation(), Sound.LEVEL_UP, 10, 2f);

					// update states and despawn the NPC
					section.set("phase", RobPhase.ROBBED.toString());
					Main.getInstance().saveConfig();

					npc.despawn();
					// create the hologram
					Location addedYSpawnLocation = spawnLocation.clone();
					addedYSpawnLocation.setY(spawnLocation.getY() + 3);

					final Hologram hologram = HologramsAPI.createHologram(Main.getInstance(), addedYSpawnLocation);
					hologram.appendTextLine(Utils.translate("&c&lThis store has recently been robbed"));
					hologram.appendTextLine(Utils.translate("&7Come back in: " + Constants.RESPAWN_TIME));
					hologram.appendTextLine(Utils.translate("&7It should be back in business by then."));

					// spawn a pigman

					new WantedUtils().spawnCops(p, 100);


					final int[] temp = {Constants.RESPAWN_TIME};
					BukkitRunnable holoUpdater = new BukkitRunnable()
					{
						@Override
						public void run(){
							hologram.clearLines();
							hologram.appendTextLine(Utils.translate("&c&lThis store has recently been robbed"));
							hologram.appendTextLine(Utils.translate("&7Come back in: " + temp[0] + " Seconds"));
							hologram.appendTextLine(Utils.translate("&7It should be back in business by then."));
							temp[0]--;
						}
					};
					holoUpdater.runTaskTimer(Main.getInstance(),0L,20L);


					// schedule the respawn
					Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
						npc.spawn(spawnLocation);
						holoUpdater.cancel();
						hologram.delete();
						section.set("phase", RobPhase.CAN_ROB.toString());
						Main.getInstance().saveConfig();
					}, 20 * Constants.RESPAWN_TIME);
				}
			};
			final boolean[] started = new boolean[1];
			BukkitRunnable beginRob = new BukkitRunnable(){
				//private boolean started;
				@Override
				public void run(){
					if(p == null || p.isDead()){
						cancel();
						halfDone.cancel();
						afterRobbery.cancel();
						Main.ctp.getTagManager().untag(p.getUniqueId());
						started[0] = false;
						return;
					}
					if(!started[0]){
						beginRobID = this.getTaskId();
						started[0] = true;
					}

					Main.ctp.getTagManager().tag(p, p);
//					temp--;
//					BarAPI.setMessage(p, Utils.translate("&a&lTime left to rob:" + temp),(temp*1.0f)/(timeToRob*20.0f) * 100f);
				}
			};

			if (RobPhase.valueOf(section.getString("phase")) == RobPhase.ROBBING) {
				p.closeInventory();
				Utils.sendMessage(p, MessageType.ERROR, "This NPC is already being robbed.");
				return;
			}

			section.set("phase", RobPhase.ROBBING.toString());
			Main.getInstance().saveConfig();

			// start the robbing process
			p.closeInventory();

			Utils.sendMessage(p, MessageType.SUCCESS, "You've started robbing this NPC.");

			// broadcast an alert to the entire server of a robbery in progress
			Bukkit.broadcastMessage(Utils.translate("&c&l(!) &c" + p.getName() + " has started robbing a store at &lx" + (int) npc.getStoredLocation().getX()
			+ ", y" + (int) npc.getStoredLocation().getY() + ", z" + (int) npc.getStoredLocation().getZ()));

			// play sound for everyone
//			for (Player each : Bukkit.getOnlinePlayers()) {
//				each.playSound(each.getLocation(), Sound.NOTE_PLING, 10, 1);
//			}

			halfDone.runTaskLater(Main.getInstance(),20 * (timeToRob/2));

			// send a status update to the player
//			int halfDone = Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getInstance(), () -> {
//				p.sendMessage(Utils.translate("&a&l&n50%&a&l of the stores loot has been collected."));
//				p.sendMessage(Utils.translate("&7Stay inside the store to finish robbing it."));
//
//				Location spawn = new Location(
//						Bukkit.getWorld(NPCConfigHelper.getNPCSection(id).getString("pigmen.world")),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.x"),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.y"),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.z"));
//
//				for (int i = 0; i < 2; i++) {
//					PigZombie pz = p.getLocation().getWorld().spawn(spawn, PigZombie.class);
//					pz.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1));
//					pz.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE, 1));
//					pz.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS, 1));
//					pz.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS, 1));
//					pz.setCanPickupItems(false);
//					pz.setCanPickupItems(false);
//					pz.setCustomName(Utils.translate("&b&lCop"));
//					pz.setCustomNameVisible(true);
//					pz.getEquipment().setItemInHand(new ItemStack(Material.IRON_SWORD));
//					pz.setTarget(p);
//				}
//			}, 20 * (timeToRob / 2));


			afterRobbery.runTaskLater(Main.getInstance(), (20 * timeToRob)+ 5);
			beginRob.runTaskTimer(Main.getInstance(),20L,20L);
			// after robbery
//			int afterRobbery = Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getInstance(), () -> {
//				// keep a record of gained loot
//				Map<Integer, String> itemsGained = new HashMap<Integer, String>();
//
//				// give loot to player
//				for (String uuid : section.getConfigurationSection("loot.items").getKeys(false)) {
//					int next = r.nextInt(100) + 1;
//
//					if (next <= Integer.parseInt(section.getString("loot.items." + uuid + ".chance").trim())) {
//						ItemStack i = section.getItemStack("loot.items." + uuid + ".item");
//						i.setAmount(section.getInt("loot.items." + uuid + ".amount"));
//
//
//						String name = i.getItemMeta().hasDisplayName() ? i.getItemMeta().getDisplayName()
//								: i.getType().toString().replace("_", " ");
//						itemsGained.put(i.getAmount(), name);
//						p.getInventory().addItem(i);
//					}
//				}
//
//				for (String uuid : section.getConfigurationSection("loot.commands").getKeys(false)) {
//					int next = r.nextInt(100) + 1;
//
//					if (next <= Integer.parseInt(section.getString("loot.commands." + uuid + ".chance").trim())) {
//						Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
//								section.getConfigurationSection("loot.commands." + uuid).getString("command")
//										.replaceAll("%PLAYER%", p.getName()));
//					}
//				}
//
//				// successfully robbed store
//
//				if (itemsGained.isEmpty()) {
//					p.sendMessage(Utils.translate("&c&lSTORE ROBBERY UNSUCCESSFUL"));
//					p.sendMessage(Utils.translate("&7The store clerk was able to escape."));
//					p.sendMessage(Utils.translate("&7No items received."));
//					p.sendMessage(Utils.translate("&6Your wanted level has increased... Start running!"));
//					wP.addWantedLevel(r.nextInt(5)+ 2);
//				} else {
//					p.sendMessage(Utils.translate("&a&lROBBED STORE SUCCESSFULLY"));
//					p.sendMessage(Utils.translate("&7You have successfully robbed this store"));
//					p.sendMessage(Utils.translate("&7and collected..."));
//
//					for (Entry<Integer, String> lootItemEntry : itemsGained.entrySet()) {
//						p.sendMessage(
//								Utils.translate("&a&l* x" + lootItemEntry.getKey() + " " + lootItemEntry.getValue()));
//					}
//					p.sendMessage(Utils.translate("&6Your wanted level has increased... Start running!"));
//					wP.addWantedLevel(r.nextInt(7)+ 3);
//					//utils.addStat(p.getUniqueId().toString(), CHALLENGE.STORES_ROBBED);
//					Clan clan = Clan.getClan(p.getUniqueId());
//					if(clan != null) {
//						clan.setGangPoints(clan.getGangPoints() + 1);
//					}
//				}
//
//				p.playSound(p.getLocation(), Sound.LEVEL_UP, 10, 2f);
//
//				// update states and despawn the NPC
//				section.set("phase", RobPhase.ROBBED.toString());
//				Main.getInstance().saveConfig();
//
//				npc.despawn();
//
//				// create the hologram
//				Location addedYSpawnLocation = spawnLocation.clone();
//				addedYSpawnLocation.setY(spawnLocation.getY() + 3);
//
//				final Hologram hologram = HologramsAPI.createHologram(Main.getInstance(), addedYSpawnLocation);
//				hologram.appendTextLine(Utils.translate("&c&lThis store has recently been robbed"));
//				hologram.appendTextLine(Utils.translate("&7Try coming back later, the store"));
//				hologram.appendTextLine(Utils.translate("&7should be back in business."));
//
//				// spawn a pigman
//				Location spawn = new Location(
//						Bukkit.getWorld(NPCConfigHelper.getNPCSection(id).getString("pigmen.world")),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.x"),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.y"),
//						NPCConfigHelper.getNPCSection(id).getDouble("pigmen.z"));
//
//				for (int i = 0; i < 5; i++) {
//					PigZombie pz = p.getLocation().getWorld().spawn(spawn, PigZombie.class);
//					pz.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1));
//					pz.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE, 1));
//					pz.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS, 1));
//					pz.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS, 1));
//					pz.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
//					pz.setCanPickupItems(false);
//					pz.setCanPickupItems(false);
//					pz.setCustomName(Utils.translate("&b&lCop"));
//					pz.setCustomNameVisible(true);
//					pz.getEquipment().setItemInHand(new ItemStack(Material.IRON_SWORD));
//					pz.setTarget(p);
//				}
//
//				// schedule the respawn
//				Bukkit.getScheduler().scheduleSyncDelayedTask(Main.getInstance(), () -> {
//					npc.spawn(spawnLocation);
//
//					hologram.delete();
//
//					section.set("phase", RobPhase.CAN_ROB.toString());
//					Main.getInstance().saveConfig();
//				}, 20 * Constants.RESPAWN_TIME);
//			}, timeToRob * 20);

			// store the robbery information object
			robs.put(p.getUniqueId(), new RobStorageObject(npc, new Integer[] { halfDone.getTaskId(), afterRobbery.getTaskId() }));
		}
	}
}
