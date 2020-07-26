/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.trade;

import java.util.Arrays;
import java.util.Map;

import java.util.HashMap;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.contract.Context;


@DataType()
class ACLSubject {
    @Property()
    private String mspId;

    @Property()
    private String role;

    public ACLSubject(String mspId, String role) {
        this.mspId = mspId;
        this.role = role;
    }

    public String getMspId() {
        return mspId;
    }

    public void setMspId(String mspId) {
        this.mspId = mspId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() == ACLSubject.class) {
            ACLSubject aclSubject = (ACLSubject) obj;
            return aclSubject.getMspId().equals(this.getMspId()) && aclSubject.getRole().equals(this.getRole());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (this.getMspId() + "," + this.getRole()).hashCode();
    }

    @Override
    public String toString() {
        return "MSP ID: " + this.getMspId() + ", Role: " + this.getRole();
    }
 }

public class AccessControlUtils {
    public final static String BUSINESS_ROLE_ATTR = "BUSINESS_ROLE";
    private final static Map<ACLSubject,String[]> aclRules = new HashMap<ACLSubject,String[]>();

    static {
        aclRules.put(new ACLSubject(Constants.importerOrgMsp, Constants.ANY_ROLE), new String[]{ "init" });
        aclRules.put(new ACLSubject(Constants.exporterOrgMsp, Constants.ANY_ROLE), new String[]{ "init" });
        aclRules.put(new ACLSubject(Constants.importerOrgMsp, Constants.IMPORTER_BANKER_ROLE), new String[]{ "makePayment", "issueLC", "existsLC", "getLC", "getLCStatus" });
        aclRules.put(new ACLSubject(Constants.exporterOrgMsp, Constants.EXPORTER_BANKER_ROLE), new String[]{ "requestPayment", "acceptLC", "existsLC", "getLC", "getLCStatus" });
        aclRules.put(new ACLSubject(Constants.importerOrgMsp, Constants.IMPORTER_ROLE), new String[]{ "requestLC", "existsLC", "getLC", "getLCStatus", "getAccountBalance" });
        aclRules.put(new ACLSubject(Constants.exporterOrgMsp, Constants.EXPORTER_ROLE), new String[]{ "existsLC", "getLC", "getLCStatus", "getAccountBalance" });
    }

    public static String GetClientMspId(Context ctx) {
        return ctx.getClientIdentity().getMSPID();
    }

    public static String GetClientRole(Context ctx) {
        return ctx.getClientIdentity().getAttributeValue(AccessControlUtils.BUSINESS_ROLE_ATTR);
    }

    public static boolean checkAccess(Context ctx, String mspId, String role, String function) {
        ACLSubject aclSubject = new ACLSubject(mspId, role);
        if (!aclRules.containsKey(aclSubject)) {
            throw new ChaincodeException("The participant " + mspId + " role " + role + " is not recognized");
        } else {
            return !Arrays.asList(aclRules.get(aclSubject)).contains(function);
        }
    }

}
