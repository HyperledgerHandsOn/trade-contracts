/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

import { Object, Property } from 'fabric-contract-api';

@Object()
export class Shipment {

    @Property()
    public carrierMSP: string;

    @Property()
    public exporterMSP: string;

    @Property()
    public descriptionOfGoods: string;

    @Property()
    public amount: number;

    @Property()
    public beneficiary: string;

}
