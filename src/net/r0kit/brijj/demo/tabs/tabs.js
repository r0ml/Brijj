function Tabs(tabListId) {
	var self = this;
	var tabs = document.getElementById(tabListId);
	self.tabLinks = tabs.getElementsByTagName("A");

	self.pickTab = function(tabId) {
		for ( var i = 0; i < self.tabLinks.length; i++) {
			var link = self.tabLinks[i];
			var loopId = link.getAttribute("data-tab");
			var me = loopId == tabId;
			document.getElementById(loopId).style.display = me ? "block" : "none";
			link.className = me ? "linkSelected " : "linkUnselected ";
		}
	}

	self.pickTab(self.tabLinks[0].getAttribute("data-tab"));

	tabs.onclick = function() {
		var tabId = event.target.getAttribute("data-tab");
		if (tabId == null) return true;
		self.pickTab(tabId);
		return false;
	}
}
