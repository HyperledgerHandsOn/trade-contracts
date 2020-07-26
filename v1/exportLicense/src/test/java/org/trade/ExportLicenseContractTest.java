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

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.json.JSONObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


public final class ExportLicenseContractTest {

    public String tradeChannelName = "tradechannel";
    public String tradeContractId = "trade";
    public String carrierMSP = "carrierMSP";
    public String regulatorMSP = Constants.regulatorOrgMsp;
    public double amount = 50000.50;
    public String descriptionOfGoods = "Wood for Toys";
    public String exporterMSP = Constants.exporterOrgMsp;
    public String tradeId = "trade-1";
    public String lcId = "lc-1";
    public String elId = "el-1";
    public String expirationDate = "12/31/2020";
    public String docBL = "{ \"docType\": \"B/L\"";
    public String docEL = "{ \"docType\": \"E/L\"";

    @Test
    public void ELInit() {
        ExportLicenseContract contract = new ExportLicenseContract();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        contract.init(ctx, tradeChannelName, tradeContractId, carrierMSP, regulatorMSP);

        verify(stub).putState(Constants.tradeChannelNameKey, tradeChannelName.getBytes(UTF_8));
        verify(stub).putState(Constants.tradeContractIdKey, tradeContractId.getBytes(UTF_8));
        verify(stub).putState(Constants.carrierMSPAttr, carrierMSP.getBytes(UTF_8));
        verify(stub).putState(Constants.regulatoryAuthorityMSPAttr, regulatorMSP.getBytes(UTF_8));
        
        boolean result = contract.existsEL(ctx, tradeId);
        assertFalse(result);
    }

    @Nested
    class ELInvocations {
        @Test
        public void ELRequest() {
            ExportLicenseContract contract = new ExportLicenseContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getStub()).thenReturn(stub);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_ROLE);

            when(stub.getState(Constants.tradeChannelNameKey)).thenReturn(tradeChannelName.getBytes(UTF_8));
            when(stub.getState(Constants.tradeContractIdKey)).thenReturn(tradeContractId.getBytes(UTF_8));
            // Intentionally omit carrier record at this time
            when(stub.getState(Constants.regulatoryAuthorityMSPAttr)).thenReturn(regulatorMSP.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("requestEL");

            boolean result = contract.existsEL(ctx, tradeId);
            assertFalse(result);

            JSONObject tradeObj = new JSONObject();
            tradeObj.put(Constants.tradeAmountAttr, amount);
            tradeObj.put(Constants.importerMSPAttr, "importerMSP");
            tradeObj.put(Constants.exporterMSPAttr, "SomeOtherMSP");
            tradeObj.put(Constants.tradeDescOfGoodsAttr, descriptionOfGoods);
            tradeObj.put(Constants.tradeStatusAttr, Constants.REQUESTED);

            JSONObject lcStatusObj = new JSONObject();
            lcStatusObj.put(Constants.StatusKey, Constants.ACCEPTED);

            // Test when it cannot access the trade chaincode or the trade is not found
            Response r1 = new Response(Response.Status.INTERNAL_SERVER_ERROR, "ERROR", new byte[] {});
            ArrayList<String> tradeArgs = new ArrayList<String>();
            tradeArgs.add(Constants.getTradeFunc);
            tradeArgs.add(tradeId);
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, tradeArgs, tradeChannelName)).thenReturn(r1);

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.requestEL(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "Error invoking '" + tradeContractId + "' chaincode, function '" + Constants.getTradeFunc + "': ");

            // Test when the status is wrong
            r1 = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r1);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestEL(ctx, tradeId);
            });

            // Test when the status is right but trade does not belong to exporter
            tradeObj.put(Constants.tradeStatusAttr, Constants.ACCEPTED);
            r1 = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, Constants.getTradeFunc, tradeId)).thenReturn(r1);
            assertThrows(ChaincodeException.class, () -> {
                contract.requestEL(ctx, tradeId);
            });

            // Test when the trade request is successful and the trade object is correct
            tradeObj.put(Constants.exporterMSPAttr, exporterMSP);
            r1 = new Response(Response.Status.SUCCESS, "OK", tradeObj.toString().getBytes(UTF_8));
            when(stub.invokeChaincodeWithStringArgs(tradeContractId, tradeArgs, tradeChannelName)).thenReturn(r1);

            thrown = assertThrows(ChaincodeException.class, () -> {
                contract.requestEL(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No Carrier recorded on ledger");

            // Now add carrier record to ledger
            when(stub.getState(Constants.carrierMSPAttr)).thenReturn(carrierMSP.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.requestEL(ctx, tradeId);

            String elKey = contract.getKey(stub, tradeId);
            ExportLicense el = new ExportLicense("", "", exporterMSP, carrierMSP, descriptionOfGoods, regulatorMSP, Constants.REQUESTED);
            String elJson = el.toJSONString();
            verify(stub).putState(elKey, elJson.getBytes(UTF_8));

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestEL(ctx, tradeId);
            });

            // Test failure when the caller is in the right org but has not been assigned the right role
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.requestEL(ctx, tradeId);
            });
        }

        @Test
        public void ELIssue() {
            ExportLicenseContract contract = new ExportLicenseContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);

            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.REGULATOR_ROLE);
            when(stub.getState(Constants.regulatoryAuthorityMSPAttr)).thenReturn(regulatorMSP.getBytes(UTF_8));
            when(stub.getFunction()).thenReturn("issueEL");

            boolean result = contract.existsEL(ctx, tradeId);
            assertFalse(result);

            String elKey = contract.getKey(stub, tradeId);

            // Test with no E/L on ledger
            when(stub.getState(elKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.issueEL(ctx, tradeId, elId, expirationDate);
            });
            assertEquals(thrown.getMessage(), "No E/L recorded for trade '" + tradeId + "'");

            // Test with already issued E/L
            ExportLicense el = new ExportLicense(elId, expirationDate, exporterMSP, carrierMSP, descriptionOfGoods, regulatorMSP, Constants.ISSUED);
            String elJson = el.toJSONString();
            when(stub.getState(elKey)).thenReturn(elJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.issueEL(ctx, tradeId, elId, expirationDate);
            verify(stub, never()).putState(elKey, elJson.getBytes(UTF_8));

            el = new ExportLicense("", "", exporterMSP, carrierMSP, descriptionOfGoods, regulatorMSP, Constants.REQUESTED);
            elJson = el.toJSONString();
            when(stub.getState(elKey)).thenReturn(elJson.getBytes(UTF_8));

            contract.beforeTransaction(ctx);    // ACL check
            contract.issueEL(ctx, tradeId, elId, expirationDate);
            el.setId(elId);
            el.setExpirationDate(expirationDate);
            el.setStatus(Constants.ISSUED);
            elJson = el.toJSONString();
            verify(stub).putState(elKey, elJson.getBytes(UTF_8));

            result = contract.existsEL(ctx, tradeId);
            assertTrue(result);

            // Test with E/L containing some other regulator MSP in the approver field
            String someOtherRegulator = "SomeOtherRegulatorMSP";
            el = new ExportLicense("", "", exporterMSP, carrierMSP, descriptionOfGoods, someOtherRegulator, Constants.REQUESTED);
            elJson = el.toJSONString();
            when(stub.getState(elKey)).thenReturn(elJson.getBytes(UTF_8));

            thrown = assertThrows(ChaincodeException.class, () -> {
                contract.issueEL(ctx, tradeId, elId, expirationDate);
            });
            assertEquals(thrown.getMessage(), "Regulator recorded on ledger '" + regulatorMSP + "' does not match E/L approver '" + someOtherRegulator + "'");

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueEL(ctx, tradeId, elId, expirationDate);
            });

            // Test failure when the caller is in the right org but has not been assigned the right role
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.issueEL(ctx, tradeId, elId, expirationDate);
            });
        }

        @Test
        public void ELGet() {
            ExportLicenseContract contract = new ExportLicenseContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.REGULATOR_ROLE);
            when(stub.getFunction()).thenReturn("getEL");

            String elKey = contract.getKey(stub, tradeId);

            // Test with no E/L on ledger
            when(stub.getState(elKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.getEL(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No E/L recorded for trade '" + tradeId + "'");

            ExportLicense el = new ExportLicense(elId, expirationDate, exporterMSP, carrierMSP, descriptionOfGoods, regulatorMSP, Constants.ISSUED);
            String elJson = el.toJSONString();
            when(stub.getState(elKey)).thenReturn(elJson.getBytes(UTF_8));

            boolean result = contract.existsEL(ctx, tradeId);
            assertTrue(result);

            contract.beforeTransaction(ctx);    // ACL check
            String elRespStr = contract.getEL(ctx, tradeId);
            ExportLicense elResp = ExportLicense.fromJSONString(elRespStr);
            assertEquals(el.getId(), elResp.getId());
            assertEquals(el.getExpirationDate(), elResp.getExpirationDate());
            assertEquals(el.getExporter(), elResp.getExporter());
            assertEquals(el.getCarrier(), elResp.getCarrier());
            assertEquals(el.getDescriptionOfGoods(), elResp.getDescriptionOfGoods());
            assertEquals(el.getApprover(), elResp.getApprover());
            assertEquals(el.getStatus(), elResp.getStatus());

            // Test successs when the caller is in the exporter org and exporter role
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_ROLE);
            contract.beforeTransaction(ctx);    // ACL check
            elRespStr = contract.getEL(ctx, tradeId);
            elResp = ExportLicense.fromJSONString(elRespStr);
            assertEquals(el.getId(), elResp.getId());
            assertEquals(el.getExpirationDate(), elResp.getExpirationDate());
            assertEquals(el.getExporter(), elResp.getExporter());
            assertEquals(el.getCarrier(), elResp.getCarrier());
            assertEquals(el.getDescriptionOfGoods(), elResp.getDescriptionOfGoods());
            assertEquals(el.getApprover(), elResp.getApprover());
            assertEquals(el.getStatus(), elResp.getStatus());

            // Test failure when the caller is in the right org but has not been assigned a role
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getEL(ctx, tradeId);
            });
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getEL(ctx, tradeId);
            });

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn("SomeOtherOrgMSP");
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getEL(ctx, tradeId);
            });
        }

        @Test
        public void ELGetStatus() {
            ExportLicenseContract contract = new ExportLicenseContract();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);

            ClientIdentity clientIdentity = mock(ClientIdentity.class);
            when(ctx.getClientIdentity()).thenReturn(clientIdentity);
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.REGULATOR_ROLE);
            when(stub.getFunction()).thenReturn("getELStatus");

            String elKey = contract.getKey(stub, tradeId);

            // Test with no E/L on ledger
            when(stub.getState(elKey)).thenReturn(new byte[] {});

            Exception thrown = assertThrows(ChaincodeException.class, () -> {
                contract.getEL(ctx, tradeId);
            });
            assertEquals(thrown.getMessage(), "No E/L recorded for trade '" + tradeId + "'");

            ExportLicense el = new ExportLicense(elId, expirationDate, exporterMSP, carrierMSP, descriptionOfGoods, regulatorMSP, Constants.ISSUED);
            String elJson = el.toJSONString();
            when(stub.getState(elKey)).thenReturn(elJson.getBytes(UTF_8));

            boolean result = contract.existsEL(ctx, tradeId);
            assertTrue(result);

            contract.beforeTransaction(ctx);    // ACL check
            String elStatus = contract.getELStatus(ctx, tradeId);
            JSONObject statusObj = new JSONObject();
            statusObj.put(Constants.StatusKey, el.getStatus());
            assertEquals(elStatus, statusObj.toString());

            // Test successs when the caller is in the exporter org and exporter role
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(Constants.EXPORTER_ROLE);
            contract.beforeTransaction(ctx);    // ACL check
            elStatus = contract.getELStatus(ctx, tradeId);
            statusObj = new JSONObject();
            statusObj.put(Constants.StatusKey, el.getStatus());
            assertEquals(elStatus, statusObj.toString());

            // Test failure when the caller is in the right org but has not been assigned a role
            when(clientIdentity.getMSPID()).thenReturn(Constants.regulatorOrgMsp);
            when(clientIdentity.getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR)).thenReturn(null);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getELStatus(ctx, tradeId);
            });
            when(clientIdentity.getMSPID()).thenReturn(Constants.exporterOrgMsp);
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getELStatus(ctx, tradeId);
            });

            // Test failure when the caller is in the wrong org
            when(clientIdentity.getMSPID()).thenReturn("SomeOtherOrgMSP");
            assertThrows(ChaincodeException.class, () -> {
                contract.beforeTransaction(ctx);    // ACL check
                contract.getELStatus(ctx, tradeId);
            });
        }
    }

}
