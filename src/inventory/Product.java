package inventory;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a product in the inventory system
 * Implements Serializable for network transmission between branches
 */
public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productId;
    private String name;
    private String description;
    private double price;
    private int quantity;
    private int minimumStock;
    private String category;
    private long lastUpdated;

    /**
     * Constructor for creating a new product
     */
    public Product(String productId, String name, String description, double price, int quantity, int minimumStock) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.minimumStock = minimumStock;
        this.category = "General";
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Constructor with category
     */
    public Product(String productId, String name, String description, double price,
            int quantity, int minimumStock, String category) {
        this(productId, name, description, price, quantity, minimumStock);
        this.category = category;
    }

    // Getters and setters
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
        updateTimestamp();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        updateTimestamp();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        updateTimestamp();
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
        updateTimestamp();
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        updateTimestamp();
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int minimumStock) {
        this.minimumStock = minimumStock;
        updateTimestamp();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        updateTimestamp();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Check if product is low on stock
     */
    public boolean isLowStock() {
        return quantity <= minimumStock;
    }

    /**
     * Check if product is out of stock
     */
    public boolean isOutOfStock() {
        return quantity <= 0;
    }

    /**
     * Check if product is overstocked (more than 3x minimum)
     */
    public boolean isOverstocked() {
        return quantity > (minimumStock * 3);
    }

    /**
     * Get stock status as a descriptive string
     */
    public String getStockStatus() {
        if (isOutOfStock()) {
            return "OUT_OF_STOCK";
        } else if (isLowStock()) {
            return "LOW_STOCK";
        } else if (isOverstocked()) {
            return "OVERSTOCKED";
        } else {
            return "NORMAL";
        }
    }

    /**
     * Thread-safe method to reduce quantity (for sales/transfers)
     * 
     * @param amount amount to reduce
     * @return true if successful, false if insufficient quantity
     */
    public synchronized boolean reduceQuantity(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (quantity >= amount) {
            quantity -= amount;
            updateTimestamp();
            return true;
        }
        return false;
    }

    /**
     * Thread-safe method to add quantity (for restocking)
     * 
     * @param amount amount to add
     */
    public synchronized void addQuantity(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        quantity += amount;
        updateTimestamp();
    }

    /**
     * Calculate how many units needed to reach optimal stock level
     * 
     * @return number of units needed (0 if already at or above optimal)
     */
    public int getReplenishmentNeeded() {
        int optimalStock = minimumStock * 2; // Optimal is 2x minimum
        return Math.max(0, optimalStock - quantity);
    }

    /**
     * Get the total value of current stock
     */
    public double getStockValue() {
        return quantity * price;
    }

    /**
     * Create a copy of this product (for thread safety)
     */
    public Product copy() {
        Product copy = new Product(productId, name, description, price, quantity, minimumStock, category);
        copy.lastUpdated = this.lastUpdated;
        return copy;
    }

    /**
     * Update the last modified timestamp
     */
    private void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Validate product data
     */
    public boolean isValid() {
        return productId != null && !productId.trim().isEmpty() &&
                name != null && !name.trim().isEmpty() &&
                price >= 0 &&
                quantity >= 0 &&
                minimumStock >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Product product = (Product) obj;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }

    @Override
    public String toString() {
        return String.format("Product{id='%s', name='%s', quantity=%d, price=%.2f, status=%s}",
                productId, name, quantity, price, getStockStatus());
    }

    /**
     * Get detailed string representation
     */
    public String toDetailedString() {
        return String.format(
                "Product[ID=%s, Name=%s, Category=%s, Quantity=%d, Price=%.2f, " +
                        "MinStock=%d, Status=%s, Value=%.2f, LastUpdated=%d]",
                productId, name, category, quantity, price, minimumStock,
                getStockStatus(), getStockValue(), lastUpdated);
    }
}