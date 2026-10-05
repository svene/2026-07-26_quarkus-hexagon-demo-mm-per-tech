import {html} from "hono/html";
import type {AuditEntryVM, AuditLogPageVM} from "./generated/vm-types";
import type {HtmlResult} from "./route-types";

// Loaded once and re-rendered as a whole by the shell's Refresh button; there are no live updates.
export const AuditLogPage = (vm: AuditLogPageVM): HtmlResult => html`
	${vm.auditEntries.length === 0
		? html`<p class="has-text-grey"><em>No audit log entries yet.</em></p>`
		: html`
			<p class="has-text-grey mb-3" id="audit-log-hint">${vm.auditEntries.length} entries (at most ${vm.limit}), newest first.</p>
			<div style="overflow-x:auto">
				<table class="table is-fullwidth is-narrow" id="audit-log" style="white-space:nowrap">
					<thead>
					<tr><th>Time</th><th>Event</th><th>Details</th></tr>
					</thead>
					<tbody>
					${vm.auditEntries.map(AuditRow)}
					</tbody>
				</table>
			</div>`}
`;

const AuditRow = (e: AuditEntryVM): HtmlResult => html`
	<tr>
		<td>${e.timestamp}</td>
		<td>${e.event}</td>
		<td>${e.details}</td>
	</tr>
`;
