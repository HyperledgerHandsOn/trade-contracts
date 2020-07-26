/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.trade;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;

import com.owlike.genson.Genson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Contract(name = "LetterOfCreditContract",
    info = @Info(title = "LetterOfCredit contract",
                description = "Manage the Letter of Credit Process from Request to Fulfillment",
                version = "1.0.0",
                license =
                        @License(name = "Apache-2.0",
                                url = ""),
                contact =
                        @Contact(email = "letterOfCredit@example.com",
                                name = "letterOfCredit",
                                url = "http://letterOfCredit.me")))
@Default
public class LetterOfCreditContract implements ContractInterface {

    private final Genson genson = new Genson();

    public  LetterOfCreditContract() {
    }

    @Override
    public void beforeTransaction(Context ctx) {
        String mspId = AccessControlUtils.GetClientMspId(ctx);
        String role = AccessControlUtils.GetClientRole(ctx);
        if (role == null) {
            role = Constants.ANY_ROLE;
        }
        String function = ctx.getStub().getFunction();

        if (AccessControlUtils.checkAccess(ctx, mspId, role, function)) {
            throw new ChaincodeException("The participant " + mspId + " cannot invoke transaction " + function);
        }
    }

    public String getLCKey(ChaincodeStub stub, String id) {
        String prefix = "LetterOfCredit";
        CompositeKey ck = stub.createCompositeKey(prefix, id);
        if (ck == null) {
            System.out.println("getLCKey() stub function returned null, generating using constructor");
            ck = new CompositeKey(prefix, id);
        }
        return ck.toString();
    }

    public String getPaymentKey(ChaincodeStub stub, String id) {
        String prefix = "Payment";
        CompositeKey ck = stub.createCompositeKey(prefix, id);
        if (ck == null) {
            System.out.println("getPaymentKey() stub function returned null, generating using constructor");
            ck = new CompositeKey(prefix, id);
        }
        return ck.toString();
    }

    public String getPaymentStatusKey(ChaincodeStub stub, String id) {
        String prefix = "PaymentStatus";
        CompositeKey ck = stub.createCompositeKey(prefix, id);
        if (ck == null) {
            System.out.println("getPaymentStatusKey() stub function returned null, generating using constructor");
            ck = new CompositeKey(prefix, id);
        }
        return ck.toString();
    }

    public String getAccountKey(ChaincodeStub stub, String id) {
        String prefix = "Account";
        CompositeKey ck = stub.createCompositeKey(prefix, id);
        if (ck == null) {
            System.out.println("getAccountKey() stub function returned null, generating using constructor");
            ck = new CompositeKey(prefix, id);
        }
        return ck.toString();
    }

    public void updateAccount(ChaincodeStub stub, BankAccount bankAccount) {
        String accountKey = getAccountKey(stub, bankAccount.getOwnerMSP());
        stub.putState(accountKey, genson.serialize(bankAccount).getBytes(UTF_8));
    }

    private BankAccount lookupAccount(ChaincodeStub stub, String ownerMSP) {
        String accountKey = getAccountKey(stub, ownerMSP);
        byte[] accountBytes = stub.getState(accountKey);
        if (accountBytes == null || accountBytes.length == 0) {
            throw new ChaincodeException("No account recorded for MSP '" + ownerMSP + "'");
        }
        return BankAccount.fromJSONString(new String(accountBytes));
    }

    @Transaction()
    public void init(Context ctx, String tradeContractId, String shippingChannelName, String shipmentContractId, String exporterMSP, String exporterBank, 
                                double exporterAccountBalance, String importerMSP, String importerBank, double importerAccountBalance) {
        ChaincodeStub stub = ctx.getStub();
        stub.putState(Constants.tradeContractIdKey, tradeContractId.getBytes(UTF_8));
        stub.putState(Constants.shippingChannelNameKey, shippingChannelName.getBytes(UTF_8));
        stub.putState(Constants.shipmentContractIdKey, shipmentContractId.getBytes(UTF_8));
        BankAccount exporterAccount = new BankAccount(exporterMSP, exporterBank, exporterAccountBalance);
        updateAccount(stub, exporterAccount);
        System.out.println("Initialized exporter account: " + exporterAccount.toJSONString());
        BankAccount importerAccount = new BankAccount(importerMSP, importerBank, importerAccountBalance);
        updateAccount(stub, importerAccount);
        System.out.println("Initialized importer account: " + importerAccount.toJSONString());
        System.out.println("L/C contract initialized with Trade contract '" + tradeContractId + "'");
    }

    @Transaction()
    public boolean existsLC(Context ctx, String tradeId) {
        // Check if L/C for the given trade instance exists
        byte[] buffer = ctx.getStub().getState(getLCKey(ctx.getStub(), tradeId));
        return (buffer != null && buffer.length > 0);
    }

    @Transaction()
    public void requestLC(Context ctx, String tradeId) {
        // Lookup trade contract ID
        Map<String, Object> tradeObj = getTrade(ctx, tradeId);
        String tradeStatus = (String) tradeObj.get(Constants.tradeStatusAttr);
        String tradeImporterMSP = (String) tradeObj.get(Constants.importerMSPAttr);
        String tradeExporterMSP = (String) tradeObj.get(Constants.exporterMSPAttr);

        if (!tradeStatus.equals(Constants.ACCEPTED)) {
            throw new ChaincodeException("'" + tradeId + "' is in '" + tradeStatus + "' state. Expected '" + Constants.ACCEPTED + "'");
        }

        // Importer, represented by an importer org MSP (currently, only 'ImporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeImporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to importer " + AccessControlUtils.GetClientMspId(ctx));
        }

        // Lookup importer account balances
        BankAccount importerAccount = lookupAccount(ctx.getStub(), tradeImporterMSP);

        // Get trade amount
        Object tradeAmountObj = tradeObj.get(Constants.tradeAmountAttr);
        double tradeAmount;
        if (tradeAmountObj instanceof Long) {
            tradeAmount = (double) ((Long) tradeAmountObj);
        } else {
            tradeAmount = (double) tradeAmountObj;
        }

        // If trade amount can't be covered by the importer's balance, reject this L/C request
        if (tradeAmount > importerAccount.getBalance()) {
            throw new ChaincodeException("'" + tradeId + "' requires amount " + tradeAmount + " but importer balance is only " + importerAccount.getBalance());
        }

        // Create L/C object and record it on the ledger: exporter is represented by its org's MSP
        LetterOfCredit lc = new LetterOfCredit("", "", tradeExporterMSP, Double.valueOf(tradeObj.get(Constants.tradeAmountAttr).toString()), new LCDoc[]{}, Constants.REQUESTED);
        String lcKey = getLCKey(ctx.getStub(), tradeId);
        String lcStr = lc.toJSONString();
        ctx.getStub().putState(lcKey, lcStr.getBytes(UTF_8));
        System.out.println("L/C request recorded with key '" + lcKey + "' and value : " + lcStr);
    }

    @Transaction()
    public void issueLC(Context ctx, String tradeId, String letterOfCreditId, String expirationDate, String... docs) {
        // Lookup L/C from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String lcKey = getLCKey(stub, tradeId);
        byte[] lcBytes = stub.getState(lcKey);
        if (lcBytes == null || lcBytes.length == 0) {
            throw new ChaincodeException("No L/C recorded for trade '" + tradeId + "'");
        }

        Map<String, Object> tradeObj = getTrade(ctx, tradeId);
        String tradeImporterMSP = (String) tradeObj.get(Constants.importerMSPAttr);
        // Importer, represented by an importer org MSP (currently, only 'ImporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeImporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to importer " + AccessControlUtils.GetClientMspId(ctx) + ". Importer's bank cannot issue LC");
        }

        // Check L/C status and issue with new attributes if required
        LetterOfCredit lc = LetterOfCredit.fromJSONString(new String(lcBytes));
        String lcStatus = lc.getStatus();
        if (lcStatus.equals(Constants.ACCEPTED)) {
            System.out.println("L/C for trade '" + tradeId + "' has already been accepted");
        } else if (lcStatus.equals(Constants.ISSUED)) {
            System.out.println("L/C for trade '" + tradeId + "' has already been issued");
        } else {
            lc.setId(letterOfCreditId);
            lc.setExpirationDate(expirationDate);
            if (docs.length > 0) {
                LCDoc[] lcDocs = new LCDoc[docs.length];
                for (int i = 0 ; i < docs.length ; i++) {
                    lcDocs[i] = new LCDoc(docs[i]);
                }
                lc.setRequiredDocs(lcDocs);
            }
            lc.setStatus(Constants.ISSUED);
            String lcStr = lc.toJSONString();
            stub.putState(lcKey, lcStr.getBytes(UTF_8));
            System.out.println("L/C issuance recorded with key '" + lcKey + "' and value : " + lcStr);
        }
    }

    @Transaction()
    public void acceptLC(Context ctx, String tradeId) {
        // Lookup L/C from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String lcKey = getLCKey(stub, tradeId);
        byte[] lcBytes = stub.getState(lcKey);
        if (lcBytes == null || lcBytes.length == 0) {
            throw new ChaincodeException("No L/C recorded for trade '" + tradeId + "'");
        }

        Map<String, Object> tradeObj = getTrade(ctx, tradeId);
        String tradeExporterMSP = (String) tradeObj.get(Constants.exporterMSPAttr);
        // Exporter, represented by an exporter org MSP (currently, only 'ExporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeExporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to exporter " + AccessControlUtils.GetClientMspId(ctx) + ". Exporter's bank cannot accept LC");
        }

        // Check L/C status and accept if required
        LetterOfCredit lc = LetterOfCredit.fromJSONString(new String(lcBytes));
        String lcStatus = lc.getStatus();
        if (lcStatus.equals(Constants.ACCEPTED)) {
            System.out.println("L/C for trade '" + tradeId + "' has already been accepted");
        } else if (lcStatus.equals(Constants.REQUESTED)) {
            throw new ChaincodeException("L/C for trade '" + tradeId + "' has not been issued");
        } else {
            lc.setStatus(Constants.ACCEPTED);
            String lcStr = lc.toJSONString();
            stub.putState(lcKey, lcStr.getBytes(UTF_8));
            System.out.println("L/C acceptance recorded with key '" + lcKey + "' and value : " + lcStr);
        }
    }

    @Transaction()
    public void requestPayment(Context ctx, String tradeId) {
        // Check if there's already a pending payment request. If yes, this is just a noop.
        ChaincodeStub stub = ctx.getStub();
        String paymentStatusKey = getPaymentStatusKey(stub, tradeId);
        byte[] paymentStatusBytes = stub.getState(paymentStatusKey);
        if (paymentStatusBytes != null && paymentStatusBytes.length > 0) {
            System.out.println("Payment request for trade '" + tradeId + "' has already been recorded and is pending");
            return;
        }

        // Get shipment location from 'shipment' contract on 'shipping' channel. If it's not set, reject this operation.
        String shipmentLocation = getShipmentLocation(ctx, tradeId);
        if (shipmentLocation == null || shipmentLocation.isEmpty()) {
            throw new ChaincodeException("Shipment location response for trade '" + tradeId + "' not set, or error while fetching");
        }

        // Get trade object from 'trade' contract
        Map<String, Object> tradeObj = getTrade(ctx, tradeId);
        String tradeExporterMSP = (String) tradeObj.get(Constants.exporterMSPAttr);
        // Exporter, represented by an exporter org MSP (currently, only 'ExporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeExporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to exporter " + AccessControlUtils.GetClientMspId(ctx) + ". Exporter's bank cannot request payment");
        }
        // Lookup amount already paid. If this matches the full value of the trade, reject the operation.
        String paymentKey = getPaymentKey(stub, tradeId);
        byte[] paymentBytes = stub.getState(paymentKey);
        if (paymentBytes == null) {
            throw new ChaincodeException("Unable to lookup payments for '" + tradeId + "'");
        }
        double tradePayment = 0.0;
        if (paymentBytes.length > 0) {
            tradePayment = Double.parseDouble(new String(paymentBytes));
        }

        Object tradeAmountObj = tradeObj.get(Constants.tradeAmountAttr);
        double tradeAmount;
        if (tradeAmountObj instanceof Long) {
            tradeAmount = (double) ((Long) tradeAmountObj);
        } else {
            tradeAmount = (double) tradeAmountObj;
        }
        if (tradeAmount == tradePayment) {
            throw new ChaincodeException("Payment for '" + tradeId + "' already made in full: " + tradeAmount);
        }

        // Get bill of lading from shipment contract. Match attributes with those in the trade object
        Map<String, Object> blObj = getBillOfLading(ctx, tradeId);
        String blExporterMSP = (String) blObj.get(Constants.blExporterMSPAttr);
        String blDescGoods = (String) blObj.get(Constants.blDescGoodsAttr);
        String blBeneficiary = (String) blObj.get(Constants.blBeneficiaryAttr);
        Object blAmountObj = tradeObj.get(Constants.blAmountAttr);
        double blAmount;
        if (blAmountObj instanceof Long) {
            blAmount = (double) ((Long) blAmountObj);
        } else {
            blAmount = (double) blAmountObj;
        }
        // Match exporter MSPs
        if (!tradeExporterMSP.equals(blExporterMSP)) {
            throw new ChaincodeException("'" + tradeId + "' exporter " + tradeExporterMSP + " does not match B/L exporter " + blExporterMSP);
        }
        // Match description of goods
        String tradeDescGoods = (String) tradeObj.get(Constants.tradeDescOfGoodsAttr);
        if (!tradeDescGoods.equals(blDescGoods)) {
            throw new ChaincodeException("'" + tradeId + "' goods " + tradeDescGoods + " don't match B/L goods " + blDescGoods);
        }
        // Match amount
        if (tradeAmount != blAmount) {
            throw new ChaincodeException("'" + tradeId + "' amount " + tradeAmount + " does not match B/L amount " + blAmount);
        }
        // Match beneficiary with trade importer
        String tradeImporterMSP = (String) tradeObj.get(Constants.importerMSPAttr);
        if (!tradeImporterMSP.equals(blBeneficiary)) {
            throw new ChaincodeException("'" + tradeId + "' importer " + tradeImporterMSP + " does not match B/L importer " + blBeneficiary);
        }

        // If the shipment is still at the source location and the amount paid is greater than zero, reject this operation.
        if (shipmentLocation.equals(Constants.sourceLocation) && tradePayment > 0) {
            throw new ChaincodeException("Shipment for '" + tradeId + "' still at source location and partial amount has already been paid");
        }

        // Record a payment request on the ledger.
        stub.putState(paymentStatusKey, Constants.REQUESTED.getBytes(UTF_8));
        System.out.println("Payment request recorded with key '" + paymentStatusKey + "' and value : " + Constants.REQUESTED);
    }

    @Transaction()
    public void makePayment(Context ctx, String tradeId) {
        // Check if there's already a pending payment request. If no, then reject this request.
        ChaincodeStub stub = ctx.getStub();
        String paymentStatusKey = getPaymentStatusKey(stub, tradeId);
        byte[] paymentStatusBytes = stub.getState(paymentStatusKey);
        if (paymentStatusBytes == null || paymentStatusBytes.length == 0) {
            throw new ChaincodeException("No payment request recorded for '" + tradeId + "'");
        }

        // Get shipment location from 'shipment' contract on 'shipping' channel.
        String shipmentLocation = getShipmentLocation(ctx, tradeId);
        if (shipmentLocation == null || shipmentLocation.isEmpty()) {
            throw new ChaincodeException("Shipment location response for trade '" + tradeId + "' not set, or error while fetching");
        }

        // Get trade object from 'trade' contract
        Map<String, Object> tradeObj = getTrade(ctx, tradeId);
        String tradeImporterMSP = (String) tradeObj.get(Constants.importerMSPAttr);
        // Importer, represented by an importer org MSP (currently, only 'ImporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeImporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to importer " + AccessControlUtils.GetClientMspId(ctx) + ". Importer's bank cannot make payment");
        }
        // Lookup amount already paid.
        String paymentKey = getPaymentKey(stub, tradeId);
        byte[] paymentBytes = stub.getState(paymentKey);
        if (paymentBytes == null) {
            throw new ChaincodeException("Unable to lookup payments for '" + tradeId + "'");
        }
        double tradePayment = 0.0;
        if (paymentBytes.length > 0) {
            tradePayment = Double.parseDouble(new String(paymentBytes));
        }
        // Get trade amount
        Object tradeAmountObj = tradeObj.get(Constants.tradeAmountAttr);
        double tradeAmount;
        if (tradeAmountObj instanceof Long) {
            tradeAmount = (double) ((Long) tradeAmountObj);
        } else {
            tradeAmount = (double) tradeAmountObj;
        }

        // Get outstanding obligation
        double paymentObligation;
        if (shipmentLocation.equals(Constants.sourceLocation)) {
            paymentObligation = tradeAmount/2;
        } else {
            paymentObligation = tradeAmount - tradePayment;
        }

        // Lookup account balances
        BankAccount importerAccount = lookupAccount(stub, tradeImporterMSP);
        String tradeExporterMSP = (String) tradeObj.get(Constants.exporterMSPAttr);
        BankAccount exporterAccount = lookupAccount(stub, tradeExporterMSP);

        // Update balances and payment
        importerAccount.setBalance(importerAccount.getBalance() - paymentObligation);
        exporterAccount.setBalance(exporterAccount.getBalance() + paymentObligation);
        tradePayment += paymentObligation;

        updateAccount(stub, importerAccount);
        updateAccount(stub, exporterAccount);
        stub.putState(paymentKey, Double.toString(tradePayment).getBytes(UTF_8));

        // Delete payment request
        stub.delState(paymentStatusKey);
    }

    @Transaction()
    public String getLC(Context ctx, String tradeId) {
        // Lookup L/C from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String lcKey = getLCKey(stub, tradeId);
        byte[] lcBytes = stub.getState(lcKey);
        if (lcBytes == null || lcBytes.length == 0) {
            throw new ChaincodeException("No L/C recorded for trade '" + tradeId + "'");
        }

        String lcStr = new String(lcBytes);
        System.out.println("Retrieved L/C from ledger: " + lcStr);
        return lcStr;
    }

    @Transaction()
    public String getLCStatus(Context ctx, String tradeId) {
        // Lookup L/C from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String lcKey = getLCKey(stub, tradeId);
        byte[] lcBytes = stub.getState(lcKey);
        if (lcBytes == null || lcBytes.length == 0) {
            throw new ChaincodeException("No L/C recorded for trade '" + tradeId + "'");
        }

        LetterOfCredit lc = LetterOfCredit.fromJSONString(new String(lcBytes));
        Map<String, Object> status = new HashMap<String, Object>() {
            private static final long serialVersionUID = 7867066038290368995L;
            {
                put(Constants.StatusKey, lc.getStatus());
            }
        };
        System.out.println("Retrieved L/C status from ledger: " + lc.getStatus());
        return genson.serialize(status);
    }

    @Transaction()
    public String getAccountBalance(Context ctx) {
        // Lookup account balance from caller's MSP Id
        ChaincodeStub stub = ctx.getStub();
        String mspId = AccessControlUtils.GetClientMspId(ctx);
        BankAccount account = lookupAccount(stub, mspId);
        Map<String, Object> balance = new HashMap<String, Object>() {
            private static final long serialVersionUID = 7867066038290368995L;
            {
                put(Constants.BalanceKey, account.getBalance());
            }
        };
        return genson.serialize(balance);
    }

    private Map<String, Object> getTrade(Context ctx, String tradeId) {
        // Look up the trade contract name
        ChaincodeStub stub = ctx.getStub();
        byte[] tcBytes = stub.getState(Constants.tradeContractIdKey);
        if (tcBytes == null || tcBytes.length == 0) {
            throw new ChaincodeException("No trade contract id recorded on ledger");
        }
        String tradeChaincodeId = new String(tcBytes);

        // Lookup trade agreemeent by invoking the trade chaincode
        Response tradeResp = ctx.getStub().invokeChaincodeWithStringArgs(tradeChaincodeId, Constants.getTradeFunc, tradeId);
        if (tradeResp.getStatus() != Response.Status.SUCCESS) {
            throw new ChaincodeException("Error invoking '" + tradeChaincodeId + "' chaincode, function '" + Constants.getTradeFunc + "'");
        }

        String trade = tradeResp.getStringPayload();
        if (trade.isEmpty()) {
            throw new ChaincodeException("Unable to locate trade ': " + tradeId + "'");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tradeObj = genson.deserialize(trade, Map.class);
        return tradeObj;
    }

    private String getShipmentLocation(Context ctx, String tradeId) {
        // Look up the trade contract name
        ChaincodeStub stub = ctx.getStub();
        byte[] schBytes = stub.getState(Constants.shippingChannelNameKey);
        if (schBytes == null || schBytes.length == 0) {
            throw new ChaincodeException("No shipping channel name recorded on ledger");
        }
        String shippingChannel = new String(schBytes);

        byte[] scBytes = stub.getState(Constants.shipmentContractIdKey);
        if (scBytes == null || scBytes.length == 0) {
            throw new ChaincodeException("No shipment contract id recorded on ledger");
        }
        String shipmentChaincodeId = new String(scBytes);

        // Lookup shipment location agreemeent by invoking the sipment chaincode
        ArrayList<String> shipmentLocationArgs = new ArrayList<String>();
        shipmentLocationArgs.add(Constants.getShipmentLocationFunc);
        shipmentLocationArgs.add(tradeId);
        Response shipmentLocationResp = ctx.getStub().invokeChaincodeWithStringArgs(shipmentChaincodeId, shipmentLocationArgs, shippingChannel);
        if (shipmentLocationResp.getStatus() != Response.Status.SUCCESS) {
            throw new ChaincodeException("Error invoking '" + shipmentChaincodeId + "' chaincode, function '" + Constants.getShipmentLocationFunc + "'");
        }

        String shipmentLocation = shipmentLocationResp.getStringPayload();
        if (shipmentLocation.isEmpty()) {
            throw new ChaincodeException("Unable to get shipment location for trade ': " + tradeId + "'");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> shipmentLocationObj = genson.deserialize(shipmentLocation, Map.class);
        Object sloc = shipmentLocationObj.get(Constants.LocationKey);
        if (sloc == null) {
            throw new ChaincodeException("Shipment location response for trade '" + tradeId + "' in unexpected format or missing 'Location' field: " + shipmentLocation);
        }
        return ((String) sloc);
    }

    private Map<String, Object> getBillOfLading(Context ctx, String tradeId) {
        // Look up the trade contract name
        ChaincodeStub stub = ctx.getStub();
        byte[] schBytes = stub.getState(Constants.shippingChannelNameKey);
        if (schBytes == null || schBytes.length == 0) {
            throw new ChaincodeException("No shipping channel name recorded on ledger");
        }
        String shippingChannel = new String(schBytes);

        byte[] scBytes = stub.getState(Constants.shipmentContractIdKey);
        if (scBytes == null || scBytes.length == 0) {
            throw new ChaincodeException("No shipment contract id recorded on ledger");
        }
        String shipmentChaincodeId = new String(scBytes);

        // Lookup shipment location agreemeent by invoking the sipment chaincode
        ArrayList<String> billOfLadingArgs = new ArrayList<String>();
        billOfLadingArgs.add(Constants.getBillOfLadingFunc);
        billOfLadingArgs.add(tradeId);
        Response shipmentLocationResp = ctx.getStub().invokeChaincodeWithStringArgs(shipmentChaincodeId, billOfLadingArgs, shippingChannel);
        if (shipmentLocationResp.getStatus() != Response.Status.SUCCESS) {
            throw new ChaincodeException("Error invoking '" + shipmentChaincodeId + "' chaincode, function '" + Constants.getBillOfLadingFunc + "'");
        }

        String billOfLading = shipmentLocationResp.getStringPayload();
        if (billOfLading.isEmpty()) {
            throw new ChaincodeException("Unable to get bill of lading for trade ': " + tradeId + "'");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> billOfLadingObj = genson.deserialize(billOfLading, Map.class);
        return billOfLadingObj;
    }
}
