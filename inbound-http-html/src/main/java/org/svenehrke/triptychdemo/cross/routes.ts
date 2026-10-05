import type {UiRoute} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";
import {AdminInventory, AdminPage, AdminRequests, AdminSupplierOrders, OrderErrors} from "./admin";
import {AuditLogPage} from "./audit-log";
import {LocationInventory, LocationsPage} from "./location";
import {ShopProducts, ShopPage} from "./shop";

/**
 * One template per UiRoute. The route names come from the Java UiRoute enum (via typescript-generator), and
 * `satisfies` makes a missing or misspelled entry a TypeScript error.
 */
export const uiRoutes = {
	AdminPage: (vm: any) => AdminPage(vm),
	AdminInventory: (vm: any) => AdminInventory(vm),
	AdminRequests: (vm: any) => AdminRequests(vm),
	AdminSupplierOrders: (vm: any) => AdminSupplierOrders(vm),
	OrderErrors: (vm: any) => OrderErrors(vm),
	LocationsPage: (vm: any) => LocationsPage(vm),
	LocationInventory: (vm: any) => LocationInventory(vm),
	ShopPage: (vm: any) => ShopPage(vm),
	ShopProducts: (vm: any) => ShopProducts(vm),
	AuditLogPage: (vm: any) => AuditLogPage(vm),

} satisfies Record<UiRoute, (vm: any) => HtmlResult>;
