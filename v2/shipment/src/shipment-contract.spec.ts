/*
 * SPDX-License-Identifier: Apache-2.0
 */

 /* tslint:disable:max-classes-per-file */
import { Context } from 'fabric-contract-api';
import { ChaincodeStub, ClientIdentity } from 'fabric-shim';
import { ShipmentContract } from '.';

import * as chai from 'chai';
import * as chaiAsPromised from 'chai-as-promised';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import winston = require('winston');

chai.should();
chai.use(chaiAsPromised);
chai.use(sinonChai);

class TestContext implements Context {
    public stub: sinon.SinonStubbedInstance<ChaincodeStub> = sinon.createStubInstance(ChaincodeStub);
    public clientIdentity: sinon.SinonStubbedInstance<ClientIdentity> = sinon.createStubInstance(ClientIdentity);
    public logger = {
        getLogger: sinon.stub().returns(sinon.createStubInstance(winston.createLogger().constructor)),
        setLevel: sinon.stub(),
     };
}

describe('ShipmentContract', () => {

    let contract: ShipmentContract;
    let ctx: TestContext;

    beforeEach(() => {
        contract = new ShipmentContract();
        ctx = new TestContext();
        ctx.stub.getState.withArgs('ShipmentLocation1001').resolves(Buffer.from('{"Location":"SOURCE"}'));
        ctx.stub.getState.withArgs('ShipmentLocation1002').resolves(Buffer.from('{"Location":"DESTINATION"}'));
        ctx.stub.getState.withArgs('Shipment1001').resolves(Buffer.from('{"carrierMSP":"CarrierOrg","exporterMSP":"ExporterOrg","descriptionOfGoods":"Mangos","amount":1000,"beneficiary":"ImportersBank"}'));
        ctx.stub.getState.withArgs('BillOfLading1003').resolves(Buffer.from('{"id":"bl1003","expirationDate":"01/01/2020","exporterMSP":"ExporterOrg","carrierMSP":"CarrierOrg","descriptionOfGoods":"Melons","amount":1000,"beneficiary":"ImportersBank","sourcePort":"London","destinationPort":"Tokyo"}'));
        ctx.stub.createCompositeKey.withArgs('Shipment', ['Location', '1001']).returns('ShipmentLocation1001');
        ctx.stub.createCompositeKey.withArgs('Shipment', ['Location', '1002']).returns('ShipmentLocation1002');
        ctx.stub.createCompositeKey.withArgs('Shipment', ['Location', '1003']).returns('ShipmentLocation1003');
        ctx.stub.createCompositeKey.withArgs('Shipment', ['1001']).returns('Shipment1001');
        ctx.stub.createCompositeKey.withArgs('Shipment', ['1002']).returns('Shipment1002');
        ctx.stub.createCompositeKey.withArgs('Shipment', ['1003']).returns('Shipment1003');
        ctx.stub.createCompositeKey.withArgs('BillOfLading', ['1001']).returns('BillOfLading1001');
        ctx.stub.createCompositeKey.withArgs('BillOfLading', ['1002']).returns('BillOfLading1002');
        ctx.stub.createCompositeKey.withArgs('BillOfLading', ['1003']).returns('BillOfLading1003');
    });

    describe('#beforeTransactions', () => {
        it('should not be allowed to proceed due to invalid MSP ID.', async () => {
            ctx.clientIdentity.getMSPID.returns('SomeMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('importer');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"prepareShipment"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should not be allowed to proceed due to invalid role.', async () => {
            ctx.clientIdentity.getMSPID.returns('ImporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('someweirdunknownrole');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"prepareShipment"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should not be allowed to proceed due to role not having access to transaction.', async () => {
            ctx.clientIdentity.getMSPID.returns('ImporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('importer');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"getShipmentLocation"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should be allowed to proceed and not throw any exceptions.', async () => {
            ctx.clientIdentity.getMSPID.returns('ExporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('exporter');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"getShipmentLocation"}'));

            await contract.beforeTransaction(ctx).should.not.throw;
        });

        it('should be allowed to proceed and not throw any exceptions.', async () => {
            ctx.clientIdentity.getMSPID.returns('ExportingEntityOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('exporter');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"prepareShipment"}'));

            await contract.beforeTransaction(ctx).should.not.throw;
        });

        it('should be allowed to proceed and not throw any exceptions.', async () => {
            ctx.clientIdentity.getMSPID.returns('CarrierOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('exporter');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"getBillOfLading"}'));

            await contract.beforeTransaction(ctx).should.not.throw;
        });

        it('should not be allowed to invoke init with any roles.', async () => {
            ctx.clientIdentity.getMSPID.returns('SomeMSP');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"init"}'));

            await contract.beforeTransaction(ctx).should.not.throw;
        });
    });

    describe('#init', () => {

        it('should succeed', async () => {
            await contract.init(ctx);
        });
    });

    describe('#prepareShipment', () => {

        it('should prepare a shipment', async () => {
            ctx.clientIdentity.getMSPID.returns('ExporterOrg');

            await contract.prepareShipment(ctx, '1003', 'CarrierOrg', 'Apples', 1000.0, 'ImportersBank');

            ctx.stub.putState.should.have.been.calledWith('ShipmentLocation1003', Buffer.from('{"Location":"SOURCE"}'));
            ctx.stub.putState.should.have.been.calledWith('Shipment1003', Buffer.from('{"carrierMSP":"CarrierOrg","exporterMSP":"ExporterOrg","descriptionOfGoods":"Apples","amount":1000,"beneficiary":"ImportersBank"}'));
        });

        it('should throw an error for a shipment that already exists with location SOURCE', async () => {
            await contract.prepareShipment(ctx, '1001', 'CarrierOrg', 'Pears', 1000.0, 'ImportersBank').should.be.rejectedWith(/Shipment for trade 1001 has already been prepared/);
        });

        it('should throw an error for a shipment that already exists with location DESTINATION', async () => {
            await contract.prepareShipment(ctx, '1002', 'CarrierOrg', 'Pears', 1000.0, 'ImportersBank').should.be.rejectedWith(/Shipment for trade 1002 has passed the preparation stage/);
        });

    });

    describe('#acceptShipmentAndIssueBL', () => {

        it('should accept a shipment and issue a BL', async () => {
            ctx.clientIdentity.getMSPID.returns('CarrierOrg');

            await contract.acceptShipmentAndIssueBL(ctx, '1001', 'bl1001', '01/01/2020', 'London', 'Tokyo');
            ctx.stub.putState.should.have.been.calledOnceWithExactly('BillOfLading1001', Buffer.from('{"id":"bl1001","expirationDate":"01/01/2020","exporterMSP":"ExporterOrg","carrierMSP":"CarrierOrg","descriptionOfGoods":"Mangos","amount":1000,"beneficiary":"ImportersBank","sourcePort":"London","destinationPort":"Tokyo"}'));

        });

        it('should throw an error if the MSP does not match', async () => {
            ctx.clientIdentity.getMSPID.returns('SomeOtherCarrierOrg');
            await contract.acceptShipmentAndIssueBL(ctx, '1001', 'bl1001', '01/01/2020', 'London', 'Tokyo').should.be.rejectedWith(/The shipment can be accepted only by applicable carrier/);
        });

        it('should throw an error for a shipment that is already in DESTINATION', async () => {
            await contract.acceptShipmentAndIssueBL(ctx, '1002', 'bl1002', '01/01/2020', 'London', 'Tokyo').should.be.rejectedWith(/Shipment for trade 1002 has passed the preparation stage/);
        });

        it('should throw an error for a shipment that was not yet prepared', async () => {
            await contract.acceptShipmentAndIssueBL(ctx, '1003', 'bl1003', '01/01/2020', 'London', 'Tokyo').should.be.rejectedWith(/Shipment location for trade 1003 has not been found/);
        });

        it('should throw an error for a record that has location but missing shipment', async () => {
            ctx.stub.getState.withArgs('ShipmentLocation1004').resolves(Buffer.from('{"Location":"SOURCE"}'));
            ctx.stub.createCompositeKey.withArgs('Shipment', ['Location', '1004']).returns('ShipmentLocation1004');

            await contract.acceptShipmentAndIssueBL(ctx, '1004', 'bl1004', '01/01/2020', 'London', 'Tokyo').should.be.rejectedWith(/Shipment for trade 1004 has not been prepared yet/);
        });
    });

    describe('#updateShipmentLocation', () => {

        it('should update shipment location to DESTINATION', async () => {
            await contract.updateShipmentLocation(ctx, '1001', 'DESTINATION');
            ctx.stub.putState.should.have.been.calledOnceWithExactly('ShipmentLocation1001', Buffer.from('{"Location":"DESTINATION"}'));
        });

        it('should throw an error for an invalid location', async () => {
            await contract.updateShipmentLocation(ctx, '1001', 'new location').should.be.rejectedWith(/Invalid value of location - must be SOURCE or DESTINATION/);
        });

        it('should throw an error for updating to same location', async () => {
            await contract.updateShipmentLocation(ctx, '1001', 'SOURCE').should.be.rejectedWith(/Shipment location for trade 1001 is already in location SOURCE/);
        });

    });

    describe('#getShipmentLocation', () => {

        it('should return a shipment location', async () => {
            await contract.getShipmentLocation(ctx, '1001').should.eventually.deep.equal({Location: 'SOURCE'});
        });

        it('should throw an error for a shipment location that does not exist', async () => {
            await contract.getShipmentLocation(ctx, '1003').should.be.rejectedWith(/Shipment location for trade 1003 has not been found/);
        });

    });

    describe('#getBillOfLading', () => {

        it('should return a bill of lading location', async () => {
            await contract.getBillOfLading(ctx, '1003').should.eventually.deep.equal({id: 'bl1003', expirationDate: '01/01/2020', exporterMSP: 'ExporterOrg', carrierMSP: 'CarrierOrg', descriptionOfGoods: 'Melons', amount: 1000.0, beneficiary: 'ImportersBank', sourcePort: 'London', destinationPort: 'Tokyo'});
        });

        it('should throw an error for a bill of lading that does not exist', async () => {
            await contract.getBillOfLading(ctx, '1001').should.be.rejectedWith(/Bill of lading for trade 1001 has not been found/);
        });

    });
});
