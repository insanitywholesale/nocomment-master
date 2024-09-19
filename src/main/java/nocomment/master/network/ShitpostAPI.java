package nocomment.master.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import nocomment.master.Server;
import nocomment.master.World;
import nocomment.master.db.Database;

public class ShitpostAPI {

	public static void handle(Socket s, DataInputStream in) throws IOException {
		DataOutputStream out = new DataOutputStream(s.getOutputStream());
		BufferedReader d = new BufferedReader(new InputStreamReader(in));
		String username = d.readLine();
		System.out.println("username " + username);
		String serverName = d.readLine();
		System.out.println("servername " + serverName);
		if (serverName.endsWith(":25565")) {
			serverName = serverName.split(":25565")[0];
		}
		String message = d.readLine();
		OptionalInt id = Database.getPlayer(username);
		if (!id.isPresent()) {
			out.writeUTF("Unknown username " + username);
			return;
		}
		int playerID = id.getAsInt();
		Optional<Connection> connOpt = Server.getServer(serverName).getLoadedWorlds().stream()
				.map(World::getOpenConnections).flatMap(Collection::stream).filter(c -> c.getIdentity() == playerID)
				.findFirst();
		if (!connOpt.isPresent()) {
			out.writeUTF("Not currently connected sorry");
			return;
		}
		connOpt.get().dispatchChatMessage(message);
		out.writeUTF("Success");
	}

}
