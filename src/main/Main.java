package main;

import client.InventoryClientApp;
import server.BranchServer;
import javafx.application.Application;

/**
 * Main entry point for the Distributed Inventory System
 * Can launch either client or server based on command line arguments
 */
public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  java main.Main client                    - Launch JavaFX client");
            System.out.println("  java main.Main server <branchId> <port>  - Launch branch server");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "client":
                // Launch JavaFX client application
                Application.launch(InventoryClientApp.class);
                break;
            case "server":
                if (args.length < 3) {
                    System.out.println("Server requires branchId and port arguments");
                    return;
                }
                try {
                    String branchId = args[1];
                    int port = Integer.parseInt(args[2]);
                    BranchServer server = new BranchServer(branchId, port);
                    server.start();
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number: " + args[2]);
                }
                break;
            default:
                System.out.println("Invalid mode. Use 'client' or 'server'");
        }
    }
}