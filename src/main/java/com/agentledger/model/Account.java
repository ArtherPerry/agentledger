package com.agentledger.model;

public record Account(int id, String name, String platform, String acctType) {
    public boolean isDigital() { return "digital".equals(acctType); }
    public String label() { return name + (platform != null ? "  ·  " + platform : ""); }
    @Override public String toString() { return label(); }   // shows nicely in a ComboBox
}