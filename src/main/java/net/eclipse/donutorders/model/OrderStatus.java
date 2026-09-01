package net.eclipse.donutorders.model;

public enum OrderStatus {
    ACTIVE,
    COMPLETE,
    CANCELLED,
    EXPIRED;

    /** Whether the order should still accept deliveries. */
    public boolean acceptsDeliveries() {
        return this == ACTIVE;
    }
}
