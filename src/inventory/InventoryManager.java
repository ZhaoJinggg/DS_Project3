package inventory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Thread-safe inventory manager for a branch
 * Handles all inventory operations with proper concurrency control
 */
public class InventoryManager {
    private final ConcurrentHashMap<String, Product> products;
    private final ReentrantReadWriteLock lock;
    private final String branchId;
    private volatile long lastModified;
    private final Object statsLock = new Object();

    // Statistics tracking
    private long totalTransactions = 0;
    private long totalItemsSold = 0;
    private long totalItemsReceived = 0;

    public InventoryManager(String branchId) {
        this.branchId = branchId;
        this.products = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastModified = System.currentTimeMillis();
        initializeDefaultProducts();
    }

    /**
     * Initialize inventory with default products
     */
    private void initializeDefaultProducts() {
        // Electronics
        addProduct(new Product("P001", "Laptop", "High-performance laptop", 999.99, 10, 3, "Electronics"));
        addProduct(new Product("P002", "Mouse", "Wireless mouse", 29.99, 25, 5, "Electronics"));
        addProduct(new Product("P003", "Keyboard", "Mechanical keyboard", 79.99, 15, 4, "Electronics"));
        addProduct(new Product("P004", "Monitor", "24-inch LED monitor", 199.99, 8, 2, "Electronics"));
        addProduct(new Product("P005", "Headphones", "Noise-cancelling headphones", 149.99, 12, 3, "Electronics"));

        // Office Supplies
        addProduct(new Product("P006", "Notebook", "A4 spiral notebook", 5.99, 50, 10, "Office"));
        addProduct(new Product("P007", "Pen Set", "Blue ink pen set (10 pack)", 12.99, 30, 8, "Office"));
        addProduct(new Product("P008", "Stapler", "Heavy-duty stapler", 24.99, 15, 5, "Office"));

        System.out.println("Initialized inventory for " + branchId + " with " + products.size() + " products");
    }

    /**
     * Add a new product to inventory
     */
    public boolean addProduct(Product product) {
        if (product == null || !product.isValid()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            products.put(product.getProductId(), product);
            updateModificationTime();
            incrementStats("add", 0, 0);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a product by ID (returns a copy for thread safety)
     */
    public Product getProduct(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return null;
        }

        lock.readLock().lock();
        try {
            Product product = products.get(productId);
            return product != null ? product.copy() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all products (returns copies for thread safety)
     */
    public List<Product> getAllProducts() {
        lock.readLock().lock();
        try {
            return products.values().stream()
                    .map(Product::copy)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get products by category
     */
    public List<Product> getProductsByCategory(String category) {
        lock.readLock().lock();
        try {
            return products.values().stream()
                    .filter(p -> category.equals(p.getCategory()))
                    .map(Product::copy)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get products matching a predicate
     */
    public List<Product> getProductsWhere(Predicate<Product> predicate) {
        lock.readLock().lock();
        try {
            return products.values().stream()
                    .filter(predicate)
                    .map(Product::copy)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get products that are low on stock
     */
    public List<Product> getLowStockProducts() {
        return getProductsWhere(Product::isLowStock);
    }

    /**
     * Get products that are out of stock
     */
    public List<Product> getOutOfStockProducts() {
        return getProductsWhere(Product::isOutOfStock);
    }

    /**
     * Get products that are overstocked
     */
    public List<Product> getOverstockedProducts() {
        return getProductsWhere(Product::isOverstocked);
    }

    /**
     * Update product quantity
     */
    public boolean updateQuantity(String productId, int newQuantity) {
        if (productId == null || newQuantity < 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null) {
                int oldQuantity = product.getQuantity();
                product.setQuantity(newQuantity);
                updateModificationTime();

                // Track statistics
                if (newQuantity > oldQuantity) {
                    incrementStats("receive", 0, newQuantity - oldQuantity);
                } else if (newQuantity < oldQuantity) {
                    incrementStats("sell", oldQuantity - newQuantity, 0);
                }
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update product price
     */
    public boolean updatePrice(String productId, double newPrice) {
        if (productId == null || newPrice < 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null) {
                product.setPrice(newPrice);
                updateModificationTime();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Transfer stock from this branch to another
     * 
     * @param productId  product to transfer
     * @param quantity   amount to transfer
     * @param fromBranch source branch (must match this branch)
     * @param toBranch   destination branch
     * @return true if transfer successful
     */
    public boolean transferStock(String productId, int quantity, String fromBranch, String toBranch) {
        if (!this.branchId.equals(fromBranch) || quantity <= 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null && product.reduceQuantity(quantity)) {
                updateModificationTime();
                incrementStats("transfer_out", quantity, 0);
                System.out.println(String.format("Transferred %d units of %s from %s to %s",
                        quantity, productId, fromBranch, toBranch));
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Receive stock from another branch
     */
    public boolean receiveStock(String productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null) {
                product.addQuantity(quantity);
                updateModificationTime();
                incrementStats("transfer_in", 0, quantity);
                System.out.println(String.format("Received %d units of %s at branch %s",
                        quantity, productId, branchId));
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Process a sale (reduce quantity)
     */
    public boolean processSale(String productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null && product.reduceQuantity(quantity)) {
                updateModificationTime();
                incrementStats("sale", quantity, 0);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Process a restock (add quantity)
     */
    public boolean processRestock(String productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product product = products.get(productId);
            if (product != null) {
                product.addQuantity(quantity);
                updateModificationTime();
                incrementStats("restock", 0, quantity);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a product from inventory
     */
    public boolean removeProduct(String productId) {
        if (productId == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            Product removed = products.remove(productId);
            if (removed != null) {
                updateModificationTime();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get total number of products
     */
    public int getProductCount() {
        return products.size();
    }

    /**
     * Get total inventory value
     */
    public double getTotalInventoryValue() {
        lock.readLock().lock();
        try {
            return products.values().stream()
                    .mapToDouble(Product::getStockValue)
                    .sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get inventory summary by category
     */
    public Map<String, Integer> getStockSummary() {
        lock.readLock().lock();
        try {
            Map<String, Integer> summary = new ConcurrentHashMap<>();
            for (Product product : products.values()) {
                summary.put(product.getProductId(), product.getQuantity());
            }
            return summary;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get inventory summary by category
     */
    public Map<String, Integer> getCategorySummary() {
        lock.readLock().lock();
        try {
            Map<String, Integer> summary = new ConcurrentHashMap<>();
            for (Product product : products.values()) {
                summary.merge(product.getCategory(), product.getQuantity(), Integer::sum);
            }
            return summary;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Search products by name or description
     */
    public List<Product> searchProducts(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String term = searchTerm.toLowerCase();
        return getProductsWhere(p -> p.getName().toLowerCase().contains(term) ||
                p.getDescription().toLowerCase().contains(term) ||
                p.getProductId().toLowerCase().contains(term));
    }

    /**
     * Get products that need replenishment
     */
    public List<String> getReplenishmentNeeds() {
        lock.readLock().lock();
        try {
            List<String> needs = new ArrayList<>();
            for (Product product : products.values()) {
                if (product.isLowStock()) {
                    int needed = product.getReplenishmentNeeded();
                    needs.add(String.format("%s needs %d units (current: %d, min: %d)",
                            product.getName(), needed, product.getQuantity(), product.getMinimumStock()));
                }
            }
            return needs;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if product exists
     */
    public boolean hasProduct(String productId) {
        return productId != null && products.containsKey(productId);
    }

    /**
     * Get branch ID
     */
    public String getBranchId() {
        return branchId;
    }

    /**
     * Get last modification timestamp
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Get inventory statistics
     */
    public InventoryStats getStats() {
        synchronized (statsLock) {
            return new InventoryStats(totalTransactions, totalItemsSold, totalItemsReceived,
                    getProductCount(), getTotalInventoryValue());
        }
    }

    /**
     * Reset all statistics
     */
    public void resetStats() {
        synchronized (statsLock) {
            totalTransactions = 0;
            totalItemsSold = 0;
            totalItemsReceived = 0;
        }
    }

    /**
     * Update modification timestamp
     */
    private void updateModificationTime() {
        lastModified = System.currentTimeMillis();
    }

    /**
     * Increment statistics counters
     */
    private void incrementStats(String operation, long sold, long received) {
        synchronized (statsLock) {
            totalTransactions++;
            totalItemsSold += sold;
            totalItemsReceived += received;
        }
    }

    @Override
    public String toString() {
        return String.format("InventoryManager{branch=%s, products=%d, lastModified=%d}",
                branchId, products.size(), lastModified);
    }

    /**
     * Inner class for inventory statistics
     */
    public static class InventoryStats {
        public final long totalTransactions;
        public final long totalItemsSold;
        public final long totalItemsReceived;
        public final int productCount;
        public final double totalValue;

        public InventoryStats(long transactions, long sold, long received, int count, double value) {
            this.totalTransactions = transactions;
            this.totalItemsSold = sold;
            this.totalItemsReceived = received;
            this.productCount = count;
            this.totalValue = value;
        }

        @Override
        public String toString() {
            return String.format("InventoryStats{transactions=%d, sold=%d, received=%d, products=%d, value=%.2f}",
                    totalTransactions, totalItemsSold, totalItemsReceived, productCount, totalValue);
        }
    }
}