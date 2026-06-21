package com.agentledger.model;

public record User(int id, int branchId, String name, String username, String role) {
    public boolean isOwner()   { return "owner".equals(role); }
    public boolean isManager() { return "manager".equals(role); }
    public boolean isCashier() { return "cashier".equals(role); }
}