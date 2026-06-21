package com.agentledger.service;

import com.agentledger.model.Account;
import com.agentledger.model.TxnType;

public class PostingRequest {
    public TxnType type;
    public Account account;       // the wallet/cash this entry affects
    public Account toAccount;     // only for wallet-to-wallet (else null)
    public long amountPya;
    public long feePya;
    public long commissionPya;
    public boolean feeOverridden;  // true if owner manually changed fee/commission
    public String senderPhone;
    public String receiverPhone;
    public String refNo;
    public String note;
}