/*
 * SPDX-License-Identifier: Apache License 2.0
 */

package org.trade;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


public final class LetterOfCreditContractTest {

    public String tradeContractId = "trade";
    public String shippingChannelName = "shippingchannel";
    public String shipmentContractId = "shipment";
    public double amount = 50000.50;
    public String descriptionOfGoods = "Wood for Toys";
    public String importerMSP = Constants.importerOrgMsp;
    public double importerBalance = 200000.00;
    public String importerBank = "ToyBank";
    public String exporterMSP = Constants.exporterOrgMsp;
    public String exporterBank = "LumberBank";
    public long exporterBalance = 100000l;
    public String tradeId = "trade-1";
    public String lcId = "lc-1";
    public String expirationDate = "12/31/2020";
    public String docBL = "B/L";
    public String docEL = "E/L";

    @Test
    public void LCInit() {
        LetterOfCreditContract contract = new LetterOfCreditContract();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        contract.init(ctx, tradeContractId, shippingChannelName, shipmentContractId, exporterMSP, exporterBank, exporterBalance, importerMSP, 
                        importerBank, importerBalance);

        verify(stub).putState(Constants.tradeContractIdKey, tradeContractId.getBytes(UTF_8));
        verify(stub).putState(Constants.shippingChannelNameKey, shippingChannelName.getBytes(UTF_8));
        verify(stub).putState(Constants.shipmentContractIdKey, shipmentContractId.getBytes(UTF_8));
        CompositeKey ck = new CompositeKey("Account", Constants.exporterOrgMsp);
        String exporterAccount = "{\"balance\":" + (double) exporterBalance + ",\"bank\":\"" + exporterBank + "\",\"ownerMSP\":\"" + exporterMSP + "\"}";
        verify(stub).putState(ck.toString(), exporterAccount.getBytes(UTF_8));
        ck = new CompositeKey("Account", Constants.importerOrgMsp);
        String importerAccount = "{\"balance\":" + importerBalance + ",\"bank\":\"" + importerBank + "\",\"ownerMSP\":\"" + importerMSP + "\"}";
        verify(stub).putState(ck.toString(), importerAccount.getBytes(UTF_8));
        
        boolean result = contract.existsLC(ctx, tradeId);
        assertFalse(result);
    }

    @Nested
    class LCInvocations {
        @Test
        public void LCRequest() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getStub()).thenReturn(stub);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_ROLE);
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            BankAccount impAcc = new BankAccount(importerMSP, importerBank, importerBalance);
            when(stub.getState(contract.getAccountKey(stub, importerMSP))).thenReturn(impAcc.toJSONString().getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("requestLC");
        
            boolean result = contract.existsLC(ctx, tradeId);
            assertFalse(result);
    
            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, "SomeOtherMSP");
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.REQUESTED);

            // Test when it cannot access the trade chaincode or the trade is not found
            Response r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestLC(ctx, tradeId);
            });
            
            // Test when the status is wrong
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestLC(ctx, tradeId);
            });

            // Test when the status is right but trade does not belong to importer
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestLC(ctx, tradeId);
            });

            // Test when the request is successful
            tradeObj.put(Constants.importerMSPAttr, importerMSP);
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            contract.beforeTransaction(ctx);    // ACL check
            contract.requestLC(ctx, tradeId);

            String lcKey = contract.getLCKey(stub, tradeId);
            LetterOfCredit lc = new LetterOfCredit("", "", exporterMSP, amount, new LCDoc[]{}, Constants.REQUESTED);
            String lcJson = lc.toJSONString();
            verify(stub).putState(lcKey, lcJson.getBytes(UTF_8));

            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));
            result = contract.existsLC(ctx, tradeId);
            assertTrue(result);

            // Test failure when importer doesn't have enough balance to cover the trade amount
            impAcc = new BankAccount(importerMSP, importerBank, amount/2);
            when(stub.getState(contract.getAccountKey(stub, importerMSP))).thenReturn(impAcc.toJSONString().getBytes(UTF_8));
            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.requestLC(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "'" + tradeId + "' requires amount " + amount + " but importer balance is only " + amount/2);

            // Reset balance so operation should ordinarily succeed
            impAcc = new BankAccount(importerMSP, importerBank, importerBalance);
            when(stub.getState(contract.getAccountKey(stub, importerMSP))).thenReturn(impAcc.toJSONString().getBytes(UTF_8));

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestLC(ctx, tradeId);
            });

            // Test failure when the caller is in the right org but in the wrong role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_BANKER_ROLE);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestLC(ctx, tradeId);
            });
        }

        @Test
        public void LCIssue() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_BANKER_ROLE);
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("issueLC");
    
            String lcKey = contract.getLCKey(stub, tradeId);

            // Test with no L/C on ledger
            when(stub.getState(lcKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            });
            assertEquals(thrown.getMessage(), "No L/C recorded for trade '" + tradeId + "'");

            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, importerMSP);
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            Response r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            // Test with already issued L/C
            LetterOfCredit lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[]{}, Constants.ISSUED);
            String lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.issueLC(ctx, tradeId, lcId, expirationDate);
            verify(stub, never()).putState(lcKey, lcJson.getBytes(UTF_8));

            // Test with already accepted L/C
            lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[]{}, Constants.ACCEPTED);
            lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.issueLC(ctx, tradeId, lcId, expirationDate);
            verify(stub, never()).putState(lcKey, lcJson.getBytes(UTF_8));

            lc = new LetterOfCredit("", "", exporterMSP, amount, new LCDoc[]{}, Constants.REQUESTED);
            lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            lc.setId(lcId);
            lc.setExpirationDate(expirationDate);
            lc.setRequiredDocs(new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) });
            lc.setStatus(Constants.ISSUED);
            lcJson = lc.toJSONString();
            verify(stub).putState(lcKey, lcJson.getBytes(UTF_8));

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            });

            // Test failure when the caller is in the right org but in the wrong role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_ROLE);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            });
        }

        @Test
        public void LCAccept() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_BANKER_ROLE);
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("acceptLC");

            String lcKey = contract.getLCKey(stub, tradeId);

            // Test with no L/C on ledger
            when(stub.getState(lcKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.acceptLC(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No L/C recorded for trade '" + tradeId + "'");

            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, importerMSP);
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            Response r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            // Test with already accepted L/C
            LetterOfCredit lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) }, Constants.ACCEPTED);
            String lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.acceptLC(ctx, tradeId);
            verify(stub, never()).putState(lcKey, lcJson.getBytes(UTF_8));

            // Test with L/C that has been requested but not issued
            lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) }, Constants.REQUESTED);
            lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            thrown = assertThrows(ChaincodeException.class, () -> {
                contract.acceptLC(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "L/C for trade '" + tradeId + "' has not been issued");

            // Test with issued L/C
            lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) }, Constants.ISSUED);
            lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.acceptLC(ctx, tradeId);
            lc.setStatus(Constants.ACCEPTED);
            lcJson = lc.toJSONString();
            verify(stub).putState(lcKey, lcJson.getBytes(UTF_8));

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            });

            // Test failure when the caller is in the right org but in the wrong role
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_ROLE);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueLC(ctx, tradeId, lcId, expirationDate, docBL, docEL);
            });
        }

        @Test
        public void PaymentRequest() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getStub()).thenReturn(stub);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_BANKER_ROLE);
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            when(stub.getState(Constants.shippingChannelNameKey)).thenReturn(shippingChannelName.getBytes(UTF_8));
            when(stub.getState(Constants.shipmentContractIdKey)).thenReturn(shipmentContractId.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("requestPayment");

            // Test when shipment location fetch fails
            JSONObject shipmentLocationObj = new JSONObject();
            shipmentLocationObj.put(Constants.LocationKey, Constants.sourceLocation);
            Response r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            ArrayList<String> shipmentReqArgs = new ArrayList<String>();
            shipmentReqArgs.add(Constants.getShipmentLocationFunc);
            shipmentReqArgs.add(tradeId);
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, shipmentReqArgs, shippingChannelName)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });
            // Make shipment location fetch succeed
            r = new Response(Response.Status.SUCCESS, "OK", shipmentLocationObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, shipmentReqArgs, shippingChannelName)).thenReturn(r);

            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, importerMSP);
            tradeObj.put(Constants.exporterMSPAttr, "SomeOtherMSP");
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            String payment = "0.0";
            when(stub.getState(contract.getPaymentKey(stub, tradeId))).thenReturn(payment.getBytes(UTF_8));

            // Test when it cannot access the trade chaincode or the trade is not found
            r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            // Test when the trade does not belong to exporter
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            // Test when the bill of lading request fails
            JSONObject billOfLadingObj = new JSONObject();
            billOfLadingObj.put(Constants.blIdAttr, "bl-1");
            billOfLadingObj.put(Constants.blExpirationDateAttr, "02/02/2020");
            billOfLadingObj.put(Constants.blExporterMSPAttr, Constants.exporterOrgMsp);
            billOfLadingObj.put(Constants.blCarrierMSPAttr, "CarrierOrgMSP");
            billOfLadingObj.put(Constants.blDescGoodsAttr, "Random Goods");
            billOfLadingObj.put(Constants.blAmountAttr, amount);
            billOfLadingObj.put(Constants.blBeneficiaryAttr, Constants.importerOrgMsp);
            billOfLadingObj.put(Constants.blSourcePortAttr, "Lumber Port");
            billOfLadingObj.put(Constants.blDestPortAttr, "Toy Port");
            r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            ArrayList<String> blReqArgs = new ArrayList<String>();
            blReqArgs.add(Constants.getBillOfLadingFunc);
            blReqArgs.add(tradeId);
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, blReqArgs, shippingChannelName)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });
            // Make B/L fetch succeed
            r = new Response(Response.Status.SUCCESS, "OK", billOfLadingObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, blReqArgs, shippingChannelName)).thenReturn(r);

            // Request should fail because goods don't match
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            billOfLadingObj.put(Constants.blDescGoodsAttr, descriptionOfGoods);
            billOfLadingObj.put(Constants.blBeneficiaryAttr, "SomeMSP");
            r = new Response(Response.Status.SUCCESS, "OK", billOfLadingObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, blReqArgs, shippingChannelName)).thenReturn(r);

            // Request should fail because importer/beneficiary don't match
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            billOfLadingObj.put(Constants.blBeneficiaryAttr, Constants.importerOrgMsp);
            r = new Response(Response.Status.SUCCESS, "OK", billOfLadingObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, blReqArgs, shippingChannelName)).thenReturn(r);

            // Test when the request is successful
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            contract.beforeTransaction(ctx);    // ACL check
            contract.requestPayment(ctx, tradeId);
            String psKey = contract.getPaymentStatusKey(stub, tradeId);
            verify(stub).putState(psKey, Constants.REQUESTED.getBytes(UTF_8));

            // Test failure when the trade payment is equal to the trade amount
            payment = Double.toString(amount);
            when(stub.getState(contract.getPaymentKey(stub, tradeId))).thenReturn(payment.getBytes(UTF_8));
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            // Test failure when location is source and payment is greater than 0
            payment = Double.toString(amount/2);
            when(stub.getState(contract.getPaymentKey(stub, tradeId))).thenReturn(payment.getBytes(UTF_8));
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestPayment(ctx, tradeId);
            });

            // Set payment to 0 so call should ordinarily succeed
            payment = "0.0";
            when(stub.getState(contract.getPaymentKey(stub, tradeId))).thenReturn(payment.getBytes(UTF_8));
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestPayment(ctx, tradeId);
            });

            // Test failure when the caller is in the right org but in the wrong role
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_ROLE);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestPayment(ctx, tradeId);
            });

            // Test success even when payment request is already recorded on the ledger
            when(stub.getState(psKey)).thenReturn(Constants.REQUESTED.getBytes(UTF_8));
            contract.requestPayment(ctx, tradeId);
        }

        @Test
        public void PaymentMake() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getStub()).thenReturn(stub);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_BANKER_ROLE);
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            when(stub.getState(Constants.shippingChannelNameKey)).thenReturn(shippingChannelName.getBytes(UTF_8));
            when(stub.getState(Constants.shipmentContractIdKey)).thenReturn(shipmentContractId.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("makePayment");
            BankAccount impAcc = new BankAccount(importerMSP, importerBank, importerBalance);
            String impAccKey = contract.getAccountKey(stub, importerMSP);
            when(stub.getState(impAccKey)).thenReturn(impAcc.toJSONString().getBytes(UTF_8));
            BankAccount expAcc = new BankAccount(exporterMSP, exporterBank, exporterBalance);
            String expAccKey = contract.getAccountKey(stub, exporterMSP);
            when(stub.getState(expAccKey)).thenReturn(expAcc.toJSONString().getBytes(UTF_8));

            // Test failure when no payment request exists
            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.makePayment(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No payment request recorded for '" + tradeId + "'");

            // Record payment request
            when(stub.getState(contract.getPaymentStatusKey(stub, tradeId))).thenReturn(Constants.REQUESTED.getBytes(UTF_8));

            // Test when shipment location fetch fails
            JSONObject shipmentLocationObj = new JSONObject();
            shipmentLocationObj.put(Constants.LocationKey, Constants.sourceLocation);
            Response r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            ArrayList<String> shipmentReqArgs = new ArrayList<String>();
            shipmentReqArgs.add(Constants.getShipmentLocationFunc);
            shipmentReqArgs.add(tradeId);
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, shipmentReqArgs, shippingChannelName)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.makePayment(ctx, tradeId);
            });
            r = new Response(Response.Status.SUCCESS, "OK", shipmentLocationObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(shipmentContractId, shipmentReqArgs, shippingChannelName)).thenReturn(r);

            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, "SomeOtherMSP");
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            String payment = "0.0";
            when(stub.getState(contract.getPaymentKey(stub, tradeId))).thenReturn(payment.getBytes(UTF_8));

            // Test when it cannot access the trade chaincode or the trade is not found
            r = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.makePayment(ctx, tradeId);
            });

            // Test when the trade does not belong to importer
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);
            assertThrows(ChaincodeException.class, () -> {
                contract.makePayment(ctx, tradeId);
            });

            // Test when the request is successful
            tradeObj.put(Constants.importerMSPAttr, importerMSP);
            r = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r);

            contract.beforeTransaction(ctx);    // ACL check
            contract.makePayment(ctx, tradeId);
            String psKey = contract.getPaymentStatusKey(stub, tradeId);
            verify(stub).delState(psKey);
            String paymentKey = contract.getPaymentKey(stub, tradeId);
            verify(stub).putState(paymentKey, Double.toString(amount/2).getBytes(UTF_8));
            String importerAccount = "{\"balance\":" + (double) (importerBalance  - amount/2) + ",\"bank\":\"" + importerBank + "\",\"ownerMSP\":\"" + importerMSP + "\"}";
            verify(stub).putState(impAccKey, importerAccount.getBytes(UTF_8));
            String exporterAccount = "{\"balance\":" + (double) (exporterBalance  + amount/2) + ",\"bank\":\"" + exporterBank + "\",\"ownerMSP\":\"" + exporterMSP + "\"}";
            verify(stub).putState(expAccKey, exporterAccount.getBytes(UTF_8));

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.makePayment(ctx, tradeId);
            });

            // Test failure when the caller is in the right org but in the wrong role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.IMPORTER_ROLE);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.makePayment(ctx, tradeId);
            });
        }

        @Test
        public void LCGet() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);
            when(stub.getFunction()).thenReturn("getLC");

            String lcKey = contract.getLCKey(stub, tradeId);

            // Test with no L/C on ledger
            when(stub.getState(lcKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.getLC(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No L/C recorded for trade '" + tradeId + "'");

            LetterOfCredit lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) }, Constants.ACCEPTED);
            String lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            boolean result = contract.existsLC(ctx, tradeId);
            assertTrue(result);

            // Test successs for different <MSP ID, role> combinations
            String mspIds[] = new String[] { Constants.importerOrgMsp, Constants.exporterOrgMsp };
            HashMap<String, String[]> roles = new HashMap<String, String[]>();
            roles.put(Constants.importerOrgMsp, new String[] { Constants.IMPORTER_BANKER_ROLE, Constants.IMPORTER_ROLE });
            roles.put(Constants.exporterOrgMsp, new String[] { Constants.EXPORTER_BANKER_ROLE, Constants.EXPORTER_ROLE });
            for (String mspId: mspIds) {
                String[] mspRoles = roles.get(mspId);
                for (String role: mspRoles) {
                    when(clientIdentity.getMSPID()).thenReturn(mspId);
                    when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(role);
                    contract.beforeTransaction(ctx);    // ACL check
                    String lcRespStr = contract.getLC(ctx, tradeId);
                    LetterOfCredit lcResp = LetterOfCredit.fromJSONString(lcRespStr);
                    assertEquals(lc.getId(), lcResp.getId());
                    assertEquals(lc.getExpirationDate(), lcResp.getExpirationDate());
                    assertEquals(lc.getBeneficiary(), lcResp.getBeneficiary());
                    assertEquals(lc.getAmount(), lcResp.getAmount());
                    assertEquals(lc.getRequiredDocs().length, lcResp.getRequiredDocs().length);
                    LCDoc[] lcDocs = lc.getRequiredDocs();
                    LCDoc[] lcDocsResp = lc.getRequiredDocs();
                    for (int i = 0 ; i < lcDocs.length ; i++) {
                        assertEquals(lcDocs[i].getDocType(), lcDocsResp[i].getDocType());
                    }
                    assertEquals(lc.getStatus(), lcResp.getStatus());
                }
            }

            // Test failure when the caller is in the right org but has not been assigned a role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLC(ctx, tradeId);
            });
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLC(ctx, tradeId);
            });

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn("SomeOtherOrgMSP");
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLC(ctx, tradeId);
            });
        }

        @Test
        public void LCGetStatus() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);
            when(stub.getFunction()).thenReturn("getLCStatus");

            String lcKey = contract.getLCKey(stub, tradeId);

            // Test with no L/C on ledger
            when(stub.getState(lcKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.getLCStatus(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No L/C recorded for trade '" + tradeId + "'");

            LetterOfCredit lc = new LetterOfCredit(lcId, expirationDate, exporterMSP, amount, new LCDoc[] { new LCDoc(docBL), new LCDoc(docEL) }, Constants.ACCEPTED);
            String lcJson = lc.toJSONString();
            when(stub.getState(lcKey)).thenReturn(lcJson.getBytes(UTF_8));

            boolean result = contract.existsLC(ctx, tradeId);
            assertTrue(result);

            // Test successs for different <MSP ID, role> combinations
            String mspIds[] = new String[] { Constants.importerOrgMsp, Constants.exporterOrgMsp };
            HashMap<String, String[]> roles = new HashMap<String, String[]>();
            roles.put(Constants.importerOrgMsp, new String[] { Constants.IMPORTER_BANKER_ROLE, Constants.IMPORTER_ROLE });
            roles.put(Constants.exporterOrgMsp, new String[] { Constants.EXPORTER_BANKER_ROLE, Constants.EXPORTER_ROLE });
            for (String mspId: mspIds) {
                String[] mspRoles = roles.get(mspId);
                for (String role: mspRoles) {
                    when(clientIdentity.getMSPID()).thenReturn(mspId);
                    when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(role);
                    contract.beforeTransaction(ctx);    // ACL check
                    String lcStatus = contract.getLCStatus(ctx, tradeId);
                    JSONObject statusObj = new JSONObject();
                    statusObj.put(Constants.StatusKey, lc.getStatus());
                    assertEquals(lcStatus, statusObj.toString());
                }
            }

            // Test failure when the caller is in the right org but has not been assigned a role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLCStatus(ctx, tradeId);
            });
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLCStatus(ctx, tradeId);
            });

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn("SomeOtherOrgMSP");
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getLCStatus(ctx, tradeId);
            });
        }

        @Test
        public void AccountGetBalance() {
            LetterOfCreditContract contract = new LetterOfCreditContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(clientIdentity.getMSPID()).thenReturn(exporterMSP);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);
            when(stub.getFunction()).thenReturn("getAccountBalance");

            String accountKey = contract.getAccountKey(stub, exporterMSP);

            // Test with no account on ledger
            when(stub.getState(accountKey)).thenReturn(new byte[] {});

            assertThrows(ChaincodeException.class, () -> {
                contract.getAccountBalance(ctx);
            });

            BankAccount impAcc = new BankAccount(importerMSP, importerBank, importerBalance);
            String impAccKey = contract.getAccountKey(stub, importerMSP);
            when(stub.getState(impAccKey)).thenReturn(impAcc.toJSONString().getBytes(UTF_8));
            BankAccount expAcc = new BankAccount(exporterMSP, exporterBank, exporterBalance);
            String expAccKey = contract.getAccountKey(stub, exporterMSP);
            when(stub.getState(expAccKey)).thenReturn(expAcc.toJSONString().getBytes(UTF_8));

            // Test successs for different <MSP ID, role> combinations
            String mspIds[] = new String[] { Constants.importerOrgMsp, Constants.exporterOrgMsp };
            HashMap<String, String[]> roles = new HashMap<String, String[]>();
            roles.put(Constants.importerOrgMsp, new String[] { Constants.IMPORTER_ROLE });
            roles.put(Constants.exporterOrgMsp, new String[] { Constants.EXPORTER_ROLE });
            HashMap<String, Double> balances = new HashMap<String, Double>();
            balances.put(Constants.importerOrgMsp, importerBalance);
            balances.put(Constants.exporterOrgMsp, (double) exporterBalance);
            for (String mspId: mspIds) {
                String[] mspRoles = roles.get(mspId);
                for (String role: mspRoles) {
                    when(clientIdentity.getMSPID()).thenReturn(mspId);
                    when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(role);
                    contract.beforeTransaction(ctx);    // ACL check
                    String accountBalance = contract.getAccountBalance(ctx);
                    JSONObject accountObj = new JSONObject(accountBalance);
                    double balance = balances.get(mspId);
                    assertEquals(balance, (double) accountObj.get(Constants.BalanceKey));
                }
            }

            // Test failure when the caller is in the right org but has not been assigned a role
            when(clientIdentity.getMSPID()).thenReturn(Constants.importerOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getAccountBalance(ctx);
            });
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getAccountBalance(ctx);
            });

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn("SomeOtherOrgMSP");
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getAccountBalance(ctx);
            });
        }
    }

}
