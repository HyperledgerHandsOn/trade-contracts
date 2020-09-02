/*
 * SPDX-License-Identifier: Apache-2.0
 */

/* tslint:disable:max-classes-per-file */
import { Context } from 'fabric-contract-api';
import { ChaincodeStub, ClientIdentity, Iterators } from 'fabric-shim';
import Long = require('long');
import { TradeContract } from '.';

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

const exporterQuery = {
    selector: {
        exporterMSP: 'ExporterOrg',
    },
    use_index: ['_design/exporterIndexDoc', 'exporterIndex'],
};

const importerQuery = {
    selector: {
        importerMSP: 'ImporterOrg',
    },
    use_index: ['_design/importerIndexDoc', 'importerIndex'],
};

const regulatorQuery = {
    selector: {
        tradeID: { $regex: '.+' },
    },
    use_index: ['_design/regulatorIndexDoc', 'regulatorIndex'],
};

class TestStateQueryIterator implements Iterators.StateQueryIterator {
    private index: number;
    private resultset: Iterators.KV[];

    constructor(resultset: Iterators.KV[]) {
        this.index = 0;
        this.resultset = resultset;
    }

    public async close(): Promise<void> {
        return Promise.resolve();
    }

    public async next(): Promise<Iterators.NextResult<Iterators.KV>> {
        let val: Iterators.KV = null;

        if (this.index < this.resultset.length) {
            val = this.resultset[this.index++];
        }

        return Promise.resolve({
            done: this.index === this.resultset.length,
            value: val,
        });
    }
}

class TestHistoryQueryIterator implements Iterators.HistoryQueryIterator {
    private index: number;
    private resultset: Iterators.KeyModification[];

    constructor(resultset: Iterators.KeyModification[]) {
        this.index = 0;
        this.resultset = resultset;
    }

    public async close(): Promise<void> {
        return Promise.resolve();
    }

    public async next(): Promise<Iterators.NextKeyModificationResult> {
        let val: Iterators.KeyModification = null;

        if (this.index < this.resultset.length) {
            val = this.resultset[this.index++];
        }

        return Promise.resolve({
            done: this.index === this.resultset.length,
            value: val,
        });
    }
}

describe('As an importer, I can enter in a trade agreement with an exporter to acquire specific goods', () => {
    let contract: TradeContract;
    let ctx: TestContext;

    async function beforeTestList(exporterObjList: any, importerObjList: any, regulatorObjList: any, clientMSP: string) {
        let exporterResultset = [];
        if (exporterObjList !== null) {
            exporterObjList.forEach((exporterObj) => {
                const exporterJsonStr: string = JSON.stringify(exporterObj);
                exporterResultset = exporterResultset.concat({ namespace: 'somenamespace', key: 'somekey', value: Buffer.from(exporterJsonStr)});
            });
        }

        let importerResultset = [];
        if (importerObjList !== null) {
            importerObjList.forEach((importerObj) => {
                const importerJsonStr: string = JSON.stringify(importerObj);
                importerResultset = importerResultset.concat({ namespace: 'somenamespace', key: 'somekey', value: Buffer.from(importerJsonStr)});
            });
        }

        let regulatorResultset = [];
        if (regulatorObjList !== null) {
            regulatorObjList.forEach((regulatorObj) => {
                const regulatorJsonStr: string = JSON.stringify(regulatorObj);
                regulatorResultset = regulatorResultset.concat({ namespace: 'somenamespace', key: 'somekey', value: Buffer.from(regulatorJsonStr)});
            });
        }

        const exporterIterator: Iterators.StateQueryIterator = new TestStateQueryIterator(exporterResultset);
        const importerIterator: Iterators.StateQueryIterator = new TestStateQueryIterator(importerResultset);
        const regulatorIterator: Iterators.StateQueryIterator = new TestStateQueryIterator(regulatorResultset);

        exporterQuery.selector.exporterMSP = clientMSP;
        importerQuery.selector.importerMSP = clientMSP;
        ctx.clientIdentity.getMSPID.returns(clientMSP);
        ctx.stub.getQueryResult.withArgs(JSON.stringify(exporterQuery)).resolves(exporterIterator);
        ctx.stub.getQueryResult.withArgs(JSON.stringify(importerQuery)).resolves(importerIterator);
        ctx.stub.getQueryResult.withArgs(JSON.stringify(regulatorQuery)).resolves(regulatorIterator);
    }

    beforeEach(() => {
        contract = new TradeContract();
        ctx = new TestContext();
        ctx.stub.getState.withArgs('1000').resolves(Buffer.from('{"tradeID":"1000", "exporterMSP": "ExporterOrg", "importerMSP": "ImporterOrg", "amount": 1000.0, "descriptionOfGoods": "Apples", "status": "REQUESTED"}'));
        ctx.stub.getState.withArgs('1001').resolves(Buffer.from('{"tradeID":"1001", "exporterMSP": "ExporterOrg", "importerMSP": "ImporterOrg", "amount": 1000.0, "descriptionOfGoods": "Apples", "status": "REQUESTED"}'));
        ctx.stub.getState.withArgs('1002').resolves(Buffer.from('{"tradeID":"1002", "exporterMSP": "ExporterOrg", "importerMSP": "ImporterOrg", "amount": 1000.0, "descriptionOfGoods": "Oranges", "status": "ACCEPTED"}'));
    });

    describe('ImporterOrg creates a trade request to an ExporterOrg Corp', () => {
        it('should create a trade.', async () => {
            ctx.clientIdentity.getMSPID.returns('ImporterOrg');

            await contract.requestTrade(ctx, '1003', 'ExporterOrg', 'Pears', 1000.0);
            ctx.stub.putState.should.have.been.calledOnceWithExactly('1003', Buffer.from('{"tradeID":"1003","exporterMSP":"ExporterOrg","importerMSP":"ImporterOrg","descriptionOfGoods":"Pears","amount":1000,"status":"REQUESTED"}'));
        });

        it('should throw an error for a trade that already exists.', async () => {
            await contract.requestTrade(ctx, '1001', 'ExporterOrg', 'Pears', 1000.0).should.be.rejected;
        });
    });

    describe('ImporterOrg reviews his trade requests', () => {
        it('should retrieve their existing trade requests', async () => {
            const obj = { tradeID: '1001', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            beforeTestList(null, [obj], [obj], 'ImporterOrg');
            await contract.listTrade(ctx).should.eventually.deep.equal([obj]);
        });

        it('should return an empty list if they have no trade requests.', async () => {
            beforeTestList(null, null, null, 'ImporterOrg');
            await contract.listTrade(ctx).should.eventually.deep.equal([]);
        });
    });

    describe('RegulatorOrg reviews recorded trades', () => {
        it('should retrieve existing trades', async () => {
            const obj = { tradeID: '1001', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            beforeTestList(null, [obj], [obj], 'RegulatorOrgMSP');
            await contract.listTrade(ctx).should.eventually.deep.equal([obj]);
        });

        it('should return an empty list if there are no recorded trades.', async () => {
            beforeTestList(null, null, null, 'RegulatorOrgMSP');
            await contract.listTrade(ctx).should.eventually.deep.equal([]);
        });
    });

    describe('ExporterOrg Corp reviews the pending trade requests', () => {
        it('should retrieve more than one trade requests where they are the exporter', async () => {
            const obj1 = {tradeID: '1001', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED'};
            const obj2 = {tradeID: '1002', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Oranges', status: 'REQUESTED'};
            beforeTestList([obj1, obj2], null, [obj1, obj2], 'ExporterOrg');
            await contract.listTrade(ctx).should.eventually.deep.equal([obj1, obj2]);
        });

        it('should retrieve existing trade requests where they are the exporter', async () => {
            const obj = { tradeID: '1001', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            beforeTestList([obj], null, [obj], 'ExporterOrg');
            await contract.listTrade(ctx).should.eventually.deep.equal([obj]);
        });

        it('should return an empty list if they have no requests.', async () => {
            beforeTestList(null, null, null, 'ExporterOrg');
            await contract.listTrade(ctx).should.eventually.deep.equal([]);
        });

        it('should be able to retrieve the status of a trade', async () => {
            await contract.getTradeStatus(ctx, '1001').should.eventually.deep.equal({Status: 'REQUESTED'});
        });

        it('should receive an error if a trade does not exist', async () => {
            await contract.getTradeStatus(ctx, '1003').should.be.rejected;
        });
    });

    describe('ExporterOrg Corp accepts a trade request', () => {
        it('should be able to accept a trade', async () => {
            await contract.acceptTrade(ctx, '1001');
            ctx.stub.putState.should.have.been.calledOnceWithExactly('1001', Buffer.from('{"tradeID":"1001","exporterMSP":"ExporterOrg","importerMSP":"ImporterOrg","amount":1000,"descriptionOfGoods":"Apples","status":"ACCEPTED"}'));
        });

        it('should not be able to accept a trade that is in the wrong state', async () => {
            await contract.acceptTrade(ctx, '1002').should.be.rejected;
        });
    });

    describe('ImporterOrg reviews his trades in range', () => {
        it('should retrieve their existing trades in the range', async () => {
            const obj1 = { tradeID: '1000', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            const obj2 = { tradeID: '1001', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            const importerObjList = [obj1, obj2];

            let importerResultset = [];

            importerObjList.forEach((importerObj) => {
                const importerJsonStr: string = JSON.stringify(importerObj);
                importerResultset = importerResultset.concat({ namespace: 'somenamespace', key: 'somekey', value: Buffer.from(importerJsonStr)});
            });

            const importerIterator: Iterators.StateQueryIterator = new TestStateQueryIterator(importerResultset);

            ctx.stub.getStateByRange.withArgs('1000', '1002').resolves(importerIterator);

            await contract.getTradesByRange(ctx, '1000', '1002').should.eventually.deep.equal([obj1, obj2]);
        });

        it('should return an empty list.', async () => {
            const importerIterator: Iterators.StateQueryIterator = new TestStateQueryIterator([]);
            ctx.stub.getStateByRange.withArgs('1005', '1010').resolves(importerIterator);

            await contract.getTradesByRange(ctx, '1005', '1010').should.eventually.deep.equal([]);
        });
    });

    describe('ImporterOrg reviews his trade history', () => {
        const date = new Date();
        const dateTs = {
            nanos: date.getUTCMilliseconds() * 100000,
            seconds: Long.fromInt(Math.trunc(date.getTime() / 1000)),
        };
        const dateTsStr = (new Date(dateTs.seconds.toInt() * 1000 + Math.round(dateTs.nanos / 1000000))).toString();
        const dateTs1 = {
            nanos: date.getUTCMilliseconds() * 100000,
            seconds: Math.trunc(date.getTime() / 1000),
        };
        const dateTsStr1 = (new Date(dateTs1.seconds * 1000 + Math.round(dateTs1.nanos / 1000000))).toString();

        it('should retrieve history of a trade', async () => {
            const obj1 = { tradeID: '1000', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'REQUESTED' };
            const obj2 = { tradeID: '1000', exporterMSP: 'ExporterOrg', importerMSP: 'ImporterOrg', amount: 1000.0, descriptionOfGoods: 'Apples', status: 'ACCEPTED' };
            const importerObjList = [obj1, obj2];

            let importerResultset = [];
            const importerInvocationResult = [];

            importerObjList.forEach((importerObj) => {
                const importerJsonStr: string = JSON.stringify(importerObj);
                importerResultset = importerResultset.concat({ txId: 'someId', timestamp: dateTs, isDelete: 'false', value: Buffer.from(importerJsonStr)});
                importerInvocationResult.push({ timestamp: dateTsStr, isDelete: 'false', tradeAgreement: importerObj, txId: 'someId'});
            });

            const importerIterator: Iterators.HistoryQueryIterator = new TestHistoryQueryIterator(importerResultset);

            ctx.stub.getHistoryForKey.withArgs('1000').resolves(importerIterator);

            await contract.getTradeHistory(ctx, '1000').should.eventually.deep.equal(importerInvocationResult);

            const exporterObjList = [obj1, obj2];

            let exporterResultset = [];
            const exporterInvocationResult = [];

            exporterObjList.forEach((exporterObj) => {
                const exporterJsonStr: string = JSON.stringify(exporterObj);
                exporterResultset = exporterResultset.concat({ txId: 'someId', timestamp: dateTs1, isDelete: 'false', value: Buffer.from(exporterJsonStr)});
                exporterInvocationResult.push({ timestamp: dateTsStr1, isDelete: 'false', tradeAgreement: exporterObj, txId: 'someId'});
            });

            const exporterIterator: Iterators.HistoryQueryIterator = new TestHistoryQueryIterator(exporterResultset);

            ctx.stub.getHistoryForKey.withArgs('1000').resolves(exporterIterator);

            await contract.getTradeHistory(ctx, '1000').should.eventually.deep.equal(exporterInvocationResult);
        });

        it('should return an empty list.', async () => {
            const importerIterator: Iterators.HistoryQueryIterator = new TestHistoryQueryIterator([]);
            ctx.stub.getHistoryForKey.withArgs('1005').resolves(importerIterator);

            await contract.getTradeHistory(ctx, '1005').should.eventually.deep.equal([]);
        });
    });

    describe('#beforeTransactions', () => {
        it('should not be allowed to proceed due to invalid MSP ID.', async () => {
            ctx.clientIdentity.getMSPID.returns('SomeMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('importer');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"requestTrade"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should not be allowed to proceed due to invalid role.', async () => {
            ctx.clientIdentity.getMSPID.returns('ImporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('someweirdunknownrole');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"requestTrade"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should not be allowed to proceed due to role not having access to transaction.', async () => {
            ctx.clientIdentity.getMSPID.returns('ImporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('importer');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"acceptTrade"}'));

            await contract.beforeTransaction(ctx).should.throw;
        });

        it('should be allowed to proceed and not throw any exceptions.', async () => {
            ctx.clientIdentity.getMSPID.returns('ExporterOrgMSP');
            ctx.clientIdentity.getAttributeValue.withArgs('BUSINESS_ROLE').returns('exporter');
            ctx.stub.getFunctionAndParameters.returns(JSON.parse('{"params":[], "fcn":"acceptTrade"}'));

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
});
