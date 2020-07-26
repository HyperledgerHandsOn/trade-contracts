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
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;

import com.owlike.genson.Genson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Contract(name = "ExportLicenseContract",
    info = @Info(title = "ExportLicense contract",
                description = "My Smart Contract",
                version = "1.0.0",
                license =
                        @License(name = "Apache-2.0",
                                url = ""),
                contact =
                        @Contact(email = "exportLicense@example.com",
                                name = "exportLicense",
                                url = "http://exportLicense.me")))
@Default
public class ExportLicenseContract implements ContractInterface {

    private final Genson genson = new Genson();

    public  ExportLicenseContract() {
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

    public String getKey(ChaincodeStub stub, String id) {
        CompositeKey ck = stub.createCompositeKey("ExportLicense", id);
        if (ck == null) {
            System.out.println("getKey() stub function returned null, generating using constructor");
            ck = new CompositeKey("ExportLicense", id);
        }
        return ck.toString();
    }

    @Transaction()
    public void init(Context ctx, String tradeChannelName, String tradeContractId, String carrierMSP, String regulatorMSP) {
        ChaincodeStub stub = ctx.getStub();
        stub.putState(Constants.tradeChannelNameKey, tradeChannelName.getBytes(UTF_8));
        stub.putState(Constants.tradeContractIdKey, tradeContractId.getBytes(UTF_8));
        stub.putState(Constants.carrierMSPAttr, carrierMSP.getBytes(UTF_8));
        stub.putState(Constants.regulatoryAuthorityMSPAttr, regulatorMSP.getBytes(UTF_8));
        System.out.println("E/L contract initialized with Trade channel '" + tradeChannelName + "', contract '" + tradeContractId + "'");
        System.out.println("Carrier (MSP): " + carrierMSP + ", Regulatory Authority (MSP) " + regulatorMSP);
    }

    @Transaction()
    public boolean existsEL(Context ctx, String tradeId) {
        // Check if E/L for the given trade instance exists
        byte[] buffer = ctx.getStub().getState(getKey(ctx.getStub(), tradeId));
        return (buffer != null && buffer.length > 0);
    }

    @Transaction()
    public void requestEL(Context ctx, String tradeId) {
        // Lookup trade channel id
        ChaincodeStub stub = ctx.getStub();
        byte[] tchBytes = stub.getState(Constants.tradeChannelNameKey);
        if (tchBytes == null || tchBytes.length == 0) {
            throw new ChaincodeException("No trade channel name recorded on ledger");
        }
        String tradeChannel = new String(tchBytes);

        // Lookup trade contract ID
        byte[] tcBytes = stub.getState(Constants.tradeContractIdKey);
        if (tcBytes == null || tcBytes.length == 0) {
            throw new ChaincodeException("No trade contract id recorded on ledger");
        }
        String tradeContractId = new String(tcBytes);

        // Lookup trade agreemeent by invoking the trade chaincode
        ArrayList<String> tradeArgs = new ArrayList<String>();
        tradeArgs.add(Constants.getTradeFunc);
        tradeArgs.add(tradeId);
        Response tradeResp = stub.invokeChaincodeWithStringArgs(tradeContractId, tradeArgs, tradeChannel);
        String trade = tradeResp.getStringPayload();
        if (tradeResp.getStatus() != Response.Status.SUCCESS) {
            throw new ChaincodeException("Error invoking '" + tradeContractId + "' chaincode, function '" + Constants.getTradeFunc + "': " + trade);
        }
        if (trade.isEmpty()) {
            throw new ChaincodeException("Unable to locate trade ': " + trade + "'");
        }
        @SuppressWarnings("unchecked")
        Map<String, String> tradeObj = genson.deserialize(trade, Map.class);
        String tradeStatus = tradeObj.get(Constants.tradeStatusAttr);
        String tradeExporterMSP = tradeObj.get(Constants.exporterMSPAttr);

        if (!tradeStatus.equals(Constants.ACCEPTED)) {
            throw new ChaincodeException("'" + tradeId + "' is in '" + tradeStatus + "' state. Expected '" + Constants.ACCEPTED + "'");
        }

        // Exporter, represented by an exporter org MSP (currently, only 'ExporterOrgMSP'), associated with this trade must match the caller's MSP
        if (!tradeExporterMSP.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not belong to exporter " + AccessControlUtils.GetClientMspId(ctx) + ". Exporter cannot request EL");
        }

        // Lookup carrier name from ledger
        byte[] carrierBytes = stub.getState(Constants.carrierMSPAttr);
        if (carrierBytes == null || carrierBytes.length == 0) {
            throw new ChaincodeException("No Carrier recorded on ledger");
        }

        // Lookup approver name from ledger
        byte[] approverBytes = stub.getState(Constants.regulatoryAuthorityMSPAttr);
        if (approverBytes == null || approverBytes.length == 0) {
            throw new ChaincodeException("No Approver recorded on ledger");
        }

        // Create E/L object and record it on the ledger
        ExportLicense el = new ExportLicense("", "", tradeExporterMSP, new String(carrierBytes), tradeObj.get(Constants.tradeDescOfGoodsAttr),
                                            new String(approverBytes), Constants.REQUESTED);
        String elKey = getKey(stub, tradeId);
        String elStr = el.toJSONString();
        stub.putState(elKey, elStr.getBytes(UTF_8));
        System.out.println("E/L issuance recorded with key '" + elKey + "' and value : " + elStr);
    }

    @Transaction()
    public void issueEL(Context ctx, String tradeId, String exportLicenseId, String expirationDate) {
        // Lookup E/L from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String elKey = getKey(stub, tradeId);
        byte[] elBytes = stub.getState(elKey);
        if (elBytes == null || elBytes.length == 0) {
            throw new ChaincodeException("No E/L recorded for trade '" + tradeId + "'");
        }

        // Lookup approver name from ledger
        byte[] regulatorBytes = stub.getState(Constants.regulatoryAuthorityMSPAttr);
        if (regulatorBytes == null || regulatorBytes.length == 0) {
            throw new ChaincodeException("No Approver recorded on ledger");
        }
        String regulator = new String(regulatorBytes);

        // Check E/L status and issue with new attributes if required
        ExportLicense el = ExportLicense.fromJSONString(new String(elBytes));
        String elStatus = el.getStatus();
        String elApprover = el.getApprover();

        if (!elApprover.equals(regulator)) {
            throw new ChaincodeException("Regulator recorded on ledger '" + regulator + "' does not match E/L approver '" + elApprover + "'");
        }

        // Regulator, represented by a regulator org MSP (currently, only 'RegulatorOrgMSP'), associated with this trade must match the caller's MSP
        if (!regulator.equals(AccessControlUtils.GetClientMspId(ctx))) {
            throw new ChaincodeException("'" + tradeId + "' does not concern regulator " + AccessControlUtils.GetClientMspId(ctx) + ". Regulator cannot issue EL");
        }

        if (elStatus.equals(Constants.ISSUED)) {
            System.out.println("E/L for trade '" + tradeId + "' has already been issued");
        } else {
            el.setId(exportLicenseId);
            el.setExpirationDate(expirationDate);
            el.setStatus(Constants.ISSUED);
            String elStr = el.toJSONString();
            stub.putState(elKey, elStr.getBytes(UTF_8));
            System.out.println("E/L issuance recorded with key '" + elKey + "' and value : " + elStr);
        }
    }

    @Transaction()
    public String getEL(Context ctx, String tradeId) {
        // Lookup E/L from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String elKey = getKey(stub, tradeId);
        byte[] elBytes = stub.getState(elKey);
        if (elBytes == null || elBytes.length == 0) {
            throw new ChaincodeException("No E/L recorded for trade '" + tradeId + "'");
        }

        String elStr = new String(elBytes);
        System.out.println("Retrieved E/L from ledger: " + elStr);
        return elStr;
    }

    @Transaction()
    public String getELStatus(Context ctx, String tradeId) {
        // Lookup E/L from given trade ID
        ChaincodeStub stub = ctx.getStub();
        String elKey = getKey(stub, tradeId);
        byte[] elBytes = stub.getState(elKey);
        if (elBytes == null || elBytes.length == 0) {
            throw new ChaincodeException("No E/L recorded for trade '" + tradeId + "'");
        }

        ExportLicense el = ExportLicense.fromJSONString(new String(elBytes));
        Map<String, String> status = new HashMap<String, String>() {
            private static final long serialVersionUID = 7542206586285367296L;
            {
                put(Constants.StatusKey, el.getStatus());
            }
        };
        System.out.println("Retrieved E/L status from ledger: " + el.getStatus());
        return genson.serialize(status);
    }

}
