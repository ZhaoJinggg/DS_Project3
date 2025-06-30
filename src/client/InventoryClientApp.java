package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import communication.*;
import inventory.Product;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;

/**
 * JavaFX client application for the distributed inventory system
 */
public class InventoryClientApp extends Application {
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private boolean connected = false;

    // UI Components
    private TableView<Product> productTable;
    private TextField serverField;
    private TextField portField;
    private Button connectButton;
    private TextArea statusArea;
    private TextField productIdField;
    private TextField quantityField;
    private Button requestButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Distributed Inventory System - Client");

        // Create UI layout
        VBox root = createUI();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            disconnect();
            Platform.exit();
        });

        primaryStage.show();
    }

    private VBox createUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        // Connection panel
        HBox connectionPanel = createConnectionPanel();

        // Product table
        productTable = createProductTable();

        // Request panel
        HBox requestPanel = createRequestPanel();

        // Status area
        statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(5);
        statusArea.setText("Ready to connect...\n");

        root.getChildren().addAll(
                new Label("Connection Settings:"),
                connectionPanel,
                new Separator(),
                new Label("Current Inventory:"),
                productTable,
                new Separator(),
                new Label("Request Replenishment:"),
                requestPanel,
                new Separator(),
                new Label("Status:"),
                statusArea);

        return root;
    }

    private HBox createConnectionPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(5));

        serverField = new TextField("localhost");
        serverField.setPromptText("Server IP");

        portField = new TextField("8101"); // Default client port
        portField.setPromptText("Port");

        connectButton = new Button("Connect");
        connectButton.setOnAction(e -> toggleConnection());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshInventory());

        panel.getChildren().addAll(
                new Label("Server:"), serverField,
                new Label("Port:"), portField,
                connectButton, refreshButton);

        return panel;
    }

    private TableView<Product> createProductTable() {
        TableView<Product> table = new TableView<>();

        TableColumn<Product, String> idCol = new TableColumn<>("Product ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("productId"));

        TableColumn<Product, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<Product, Integer> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<Product, Double> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Product, Integer> minStockCol = new TableColumn<>("Min Stock");
        minStockCol.setCellValueFactory(new PropertyValueFactory<>("minimumStock"));

        table.getColumns().addAll(idCol, nameCol, quantityCol, priceCol, minStockCol);
        table.setPrefHeight(300);

        return table;
    }

    private HBox createRequestPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(5));

        productIdField = new TextField();
        productIdField.setPromptText("Product ID");

        quantityField = new TextField();
        quantityField.setPromptText("Quantity");

        requestButton = new Button("Request Replenishment");
        requestButton.setOnAction(e -> requestReplenishment());
        requestButton.setDisable(true);

        panel.getChildren().addAll(
                new Label("Product ID:"), productIdField,
                new Label("Quantity:"), quantityField,
                requestButton);

        return panel;
    }

    private void toggleConnection() {
        if (connected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        try {
            String server = serverField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());

            socket = new Socket(server, port);
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());

            connected = true;
            connectButton.setText("Disconnect");
            requestButton.setDisable(false);

            appendStatus("Connected to " + server + ":" + port);

            // Start message listener
            Thread messageListener = new Thread(this::listenForMessages);
            messageListener.setDaemon(true);
            messageListener.start();

            // Request initial inventory
            refreshInventory();

        } catch (Exception e) {
            appendStatus("Connection failed: " + e.getMessage());
        }
    }

    private void disconnect() {
        if (!connected)
            return;

        try {
            if (outputStream != null) {
                Message disconnect = new Message(MessageType.CLIENT_DISCONNECT, "client", "");
                outputStream.writeObject(disconnect);
                outputStream.flush();
            }
        } catch (IOException e) {
            // Ignore
        }

        closeConnections();
        connected = false;
        connectButton.setText("Connect");
        requestButton.setDisable(true);
        appendStatus("Disconnected from server");
    }

    private void closeConnections() {
        try {
            if (inputStream != null)
                inputStream.close();
        } catch (IOException e) {
            /* ignore */ }

        try {
            if (outputStream != null)
                outputStream.close();
        } catch (IOException e) {
            /* ignore */ }

        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            /* ignore */ }
    }

    private void refreshInventory() {
        if (!connected) {
            appendStatus("Not connected to server");
            return;
        }

        try {
            Message query = new Message(MessageType.STOCK_QUERY, "client", "");
            outputStream.writeObject(query);
            outputStream.flush();
        } catch (IOException e) {
            appendStatus("Failed to refresh inventory: " + e.getMessage());
        }
    }

    private void requestReplenishment() {
        if (!connected) {
            appendStatus("Not connected to server");
            return;
        }

        try {
            String productId = productIdField.getText().trim();
            String quantityText = quantityField.getText().trim();

            if (productId.isEmpty() || quantityText.isEmpty()) {
                appendStatus("Please enter both Product ID and Quantity");
                return;
            }

            int quantity = Integer.parseInt(quantityText);
            if (quantity <= 0) {
                appendStatus("Quantity must be positive");
                return;
            }

            Message request = new Message(MessageType.REPLENISHMENT_REQUEST, "client", "", productId,
                    System.currentTimeMillis());
            request.putData("quantity", quantity);

            outputStream.writeObject(request);
            outputStream.flush();

            appendStatus("Requested " + quantity + " units of " + productId);

            // Clear fields
            productIdField.clear();
            quantityField.clear();

        } catch (NumberFormatException e) {
            appendStatus("Invalid quantity format");
        } catch (IOException e) {
            appendStatus("Failed to send request: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        while (connected) {
            try {
                Object obj = inputStream.readObject();
                if (obj instanceof Message) {
                    handleMessage((Message) obj);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    Platform.runLater(() -> appendStatus("Connection lost: " + e.getMessage()));
                    connected = false;
                }
                break;
            }
        }
    }

    private void handleMessage(Message message) {
        Platform.runLater(() -> {
            switch (message.getType()) {
                case STOCK_RESPONSE:
                    handleStockResponse(message);
                    break;
                case REPLENISHMENT_RESPONSE:
                    handleReplenishmentResponse(message);
                    break;
                case STATUS_UPDATE:
                    handleStatusUpdate(message);
                    break;
                case ACK:
                    String welcomeMessage = message.getStringData("message");
                    if (welcomeMessage != null) {
                        appendStatus(welcomeMessage);
                    }
                    break;
                default:
                    appendStatus("Received: " + message.getType());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleStockResponse(Message message) {
        Object productsData = message.getData("products");
        Object productData = message.getData("product");

        if (productsData instanceof List) {
            List<Product> products = (List<Product>) productsData;
            productTable.getItems().clear();
            productTable.getItems().addAll(products);
            appendStatus("Inventory updated - " + products.size() + " products");
        } else if (productData instanceof Product) {
            Product product = (Product) productData;
            updateProductInTable(product);
            appendStatus("Product updated: " + product.getName());
        }
    }

    private void handleReplenishmentResponse(Message message) {
        String status = message.getStringData("status");
        String productId = message.getStringData("productId");
        Integer quantity = message.getIntData("quantity");

        appendStatus("Replenishment " + status + " for " + quantity + " units of " + productId);
    }

    private void handleStatusUpdate(Message message) {
        String productId = message.getStringData("productId");
        Integer quantity = message.getIntData("quantity");
        Object productData = message.getData("product");

        if (productData instanceof Product) {
            Product product = (Product) productData;
            updateProductInTable(product);
            appendStatus("Stock updated: " + productId + " now has " + quantity + " units");
        }
    }

    private void updateProductInTable(Product updatedProduct) {
        for (int i = 0; i < productTable.getItems().size(); i++) {
            Product product = productTable.getItems().get(i);
            if (product.getProductId().equals(updatedProduct.getProductId())) {
                productTable.getItems().set(i, updatedProduct);
                break;
            }
        }
    }

    private void appendStatus(String message) {
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
        statusArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}