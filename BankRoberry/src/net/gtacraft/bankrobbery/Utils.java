package net.gtacraft.bankrobbery;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class Utils {
	public static void sendMessage(Player p, MessageType type, String message) {
		if (type == MessageType.SUCCESS) {
			p.sendMessage(Utils.translate("&a" + message));
		} else if (type == MessageType.ERROR) {
			p.sendMessage(Utils.translate("&4" + message));
		}
	}
	
	public static String translate(String str) {
		return ChatColor.translateAlternateColorCodes('&', str);
	}
}
