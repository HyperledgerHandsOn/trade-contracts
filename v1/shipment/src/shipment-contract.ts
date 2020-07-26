/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Context, Contract, Info, Returns, Transaction } from 'fabric-contract-api';
import { Iterators } from 'fabric-shim-api';
import { BillOfLading } from './billoflading';
import { Shipment } from './shipment';
import { ShipmentLocation } from './shipmentlocation';

const ANY_ROLE = 'anyRole';
const BUSINESS_ROLE = 'BUSINESS_ROLE';

@Info({ title: 'ShipmentContract', description: 'Goods Shipment SmartContract' })
export class ShipmentContract extends Contract {

    private static getAclSubject(mspIdVal: string, roleVal: string) {
        return JSON.stringify({ mspId: mspIdVal, role: roleVal });        // Deterministic because of the key order
    }

    private aclRules = {};

    constructor() {
        super('ShipmentContract');
        this.aclRules[ShipmentContract.getAclSubject('ExporterOrgMSP', 'any')] = [ 'init' ];
        this.aclRules[ShipmentContract.getAclSubject('ImporterOrgMSP', 'any')] = [ 'init' ];
        this.aclRules[ShipmentContract.getAclSubject('CarrierOrgMSP', 'any')] = [ 'init' ];
        this.aclRules[ShipmentContract.getAclSubject('ExporterOrgMSP', 'exporter')] = [ 'prepareShipment', 'getShipmentLocation', 'getBillOfLading' ];
        this.aclRules[ShipmentContract.getAclSubject('ImporterOrgMSP', 'importer')] = [ 'getShipmentLocation', 'getBillOfLading' ];
        this.aclRules[ShipmentContract.getAclSubject('CarrierOrgMSP', 'carrier')] = [ 'acceptShipmentAndIssueBL', 'updateShipmentLocation', 'getShipmentLocation', 'getBillOfLading' ];
        this.aclRules[ShipmentContract.getAclSubject('ExporterOrgMSP', 'exporter_banker')] = [ 'getShipmentLocation', 'getBillOfLading' ];
        this.aclRules[ShipmentContract.getAclSubject('ImporterOrgMSP', 'importer_banker')] = [ 'getShipmentLocation', 'getBillOfLading' ];
    }

    public async beforeTransaction(ctx: Context) {
        const mspId = ctx.clientIdentity.getMSPID();
        let role = ctx.clientIdentity.getAttributeValue(BUSINESS_ROLE);
        if (!role) {
            role = 'any';
        }
        const tx = ctx.stub.getFunctionAndParameters().fcn;

        const aclSubject = ShipmentContract.getAclSubject(mspId, role);
        if (!this.aclRules.hasOwnProperty(aclSubject)) {
            throw new Error(`The participant belonging to MSP ${mspId} and role ${role} is not recognized`);
        }

        if (!this.aclRules[aclSubject].includes(tx)) {
            throw new Error(`The participant belonging to MSP ${mspId} and role ${role} cannot invoke transaction ${tx}`);
        }
    }

    /**
     * Perform any setup of the ledger that might be required.
     * @param {Context} ctx the transaction context
     */
    @Transaction()
    public async init(ctx: Context) {
        console.log('Initializing the shipment contract');
    }

    @Transaction()
    public async prepareShipment(ctx: Context, tradeId: string, carrierMSP: string, descriptionOfGoods: string, amount: number, beneficiary: string): Promise<void> {

        // Record shipment with the passed parameters. Also include client MSP ID as the exporter.

        // Lookup shipment location from the ledger
        let shipmentLocation = null;

        try {
            shipmentLocation = await this.getShipmentLocation(ctx, tradeId);
        } catch {
            // If shipment location is not found, create new location and shipment
        }

        if (!!shipmentLocation) {
            if (shipmentLocation.Location === 'SOURCE') {
                throw new Error(`Shipment for trade ${tradeId} has already been prepared`);
            } else { // DESTINATION
                throw new Error(`Shipment for trade ${tradeId} has passed the preparation stage`);
            }
        }

        // Create and store shipment location
        const slKey = this.getShipmentLocationKey(ctx, tradeId);
        shipmentLocation = new ShipmentLocation();
        shipmentLocation.Location = 'SOURCE';

        let buffer = Buffer.from(JSON.stringify(shipmentLocation));
        await ctx.stub.putState(slKey, buffer);

        // Create and store shipment
        const sKey = this.getShipmentKey(ctx, tradeId);
        const shipment = new Shipment();
        shipment.carrierMSP = carrierMSP;
        shipment.exporterMSP = ctx.clientIdentity.getMSPID();
        shipment.descriptionOfGoods = descriptionOfGoods;
        shipment.amount = amount;
        shipment.beneficiary = beneficiary;

        buffer = Buffer.from(JSON.stringify(shipment));
        await ctx.stub.putState(sKey, buffer);
    }

    @Transaction()
    public async acceptShipmentAndIssueBL(ctx: Context, tradeId: string, blId: string, expirationDate: string, sourcePort: string, destinationPort: string): Promise<void> {
        // Fetch shipment record (or collection of attributes) based on 'tradeId'
        // Match carrierMSP in shipment record to client's MSP ID
        // Create B/L using shipment attributes and passed parameters

        // Lookup shipment location from the ledger
        const shipmentLocation = await this.getShipmentLocation(ctx, tradeId);

        if (shipmentLocation.Location !== 'SOURCE') {
            throw new Error(`Shipment for trade ${tradeId} has passed the preparation stage`);
        }

        // Lookup shipment
        const shipment = await this.getShipment(ctx, tradeId);

        // Check if client is the shipment's carrier
        if (shipment.carrierMSP !== ctx.clientIdentity.getMSPID()) {
            throw new Error(`The shipment can be accepted only by applicable carrier`);
        }

        // Create and record a B/L
        const billOfLading = new BillOfLading();
        billOfLading.id = blId;
        billOfLading.expirationDate = expirationDate;
        billOfLading.exporterMSP = shipment.exporterMSP;
        billOfLading.carrierMSP = shipment.carrierMSP;
        billOfLading.descriptionOfGoods = shipment.descriptionOfGoods;
        billOfLading.amount = shipment.amount;
        billOfLading.beneficiary = shipment.beneficiary;
        billOfLading.sourcePort = sourcePort;
        billOfLading.destinationPort = destinationPort;

        const blKey = this.getBLKey(ctx, tradeId);
        const buffer = Buffer.from(JSON.stringify(billOfLading));
        await ctx.stub.putState(blKey, buffer);
    }

    @Transaction()
    public async updateShipmentLocation(ctx: Context, tradeId: string, location: string): Promise<void> {
        if (location !== 'SOURCE' && location !== 'DESTINATION') {
            throw new Error(`Invalid value of location - must be SOURCE or DESTINATION`);
        }

        const shipmentLocation = await this.getShipmentLocation(ctx, tradeId);

        if (shipmentLocation.Location === location) {
            throw new Error(`Shipment location for trade ${tradeId} is already in location ${location}`);
        }

        shipmentLocation.Location = location;

        const slKey = this.getShipmentLocationKey(ctx, tradeId);
        const buffer = Buffer.from(JSON.stringify(shipmentLocation));
        await ctx.stub.putState(slKey, buffer);
    }

    @Transaction(false)
    @Returns('ShipmentLocation')
    public async getShipmentLocation(ctx: Context, tradeId: string): Promise<ShipmentLocation> {
        const slKey = this.getShipmentLocationKey(ctx, tradeId);
        const buffer = await ctx.stub.getState(slKey);

        if (!buffer || buffer.length === 0) {
            throw new Error(`Shipment location for trade ${tradeId} has not been found`);
        }

        const shipmentLocation = JSON.parse(buffer.toString()) as ShipmentLocation;
        return shipmentLocation;
    }

    @Transaction(false)
    @Returns('BillOfLading')
    public async getBillOfLading(ctx: Context, tradeId: string): Promise<BillOfLading> {
        const blKey = this.getBLKey(ctx, tradeId);
        const buffer = await ctx.stub.getState(blKey);

        if (!buffer || buffer.length === 0) {
            throw new Error(`Bill of lading for trade ${tradeId} has not been found`);
        }

        const billOfLading = JSON.parse(buffer.toString()) as BillOfLading;
        return billOfLading;
    }

    private async getShipment(ctx: Context, tradeId: string): Promise<Shipment> {
        const sKey = this.getShipmentKey(ctx, tradeId);
        const buffer = await ctx.stub.getState(sKey);

        if (!buffer || buffer.length === 0) {
            throw new Error(`Shipment for trade ${tradeId} has not been prepared yet`);
        }

        const shipment = JSON.parse(buffer.toString()) as Shipment;
        return shipment;
    }

    private getBLKey(ctx: Context, tradeId: string): string {
        return ctx.stub.createCompositeKey('BillOfLading', [tradeId]);
    }

    private getShipmentLocationKey(ctx: Context, tradeId: string): string {
        return ctx.stub.createCompositeKey('Shipment', ['Location', tradeId]);
    }

    private getShipmentKey(ctx: Context, tradeId: string): string {
        return ctx.stub.createCompositeKey('Shipment', [tradeId]);
    }

}
