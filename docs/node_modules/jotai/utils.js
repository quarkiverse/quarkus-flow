'use strict';

var utils$1 = require('jotai/vanilla/utils');
var utils = require('jotai/react/utils');



Object.keys(utils$1).forEach(function (k) {
	if (k !== 'default' && !Object.prototype.hasOwnProperty.call(exports, k)) Object.defineProperty(exports, k, {
		enumerable: true,
		get: function () { return utils$1[k]; }
	});
});
Object.keys(utils).forEach(function (k) {
	if (k !== 'default' && !Object.prototype.hasOwnProperty.call(exports, k)) Object.defineProperty(exports, k, {
		enumerable: true,
		get: function () { return utils[k]; }
	});
});
