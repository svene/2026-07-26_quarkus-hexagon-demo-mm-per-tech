document.addEventListener('alpine:init', () => {
	console.log('init store')
	Alpine.store('darkMode', {
		on: true,

		toggle() {
			this.on = ! this.on
		},

		get theme() {
			return this.on ? 'dark' : 'light'
		},
		get icon() {
			return this.on ? 'light_mode' : 'dark_mode'
		}
	})
});
