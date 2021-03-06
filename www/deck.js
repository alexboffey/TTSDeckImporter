$(document).ready(function(){
	var isChrome = navigator.userAgent.toLowerCase().indexOf('chrome') > -1;
	if(!isChrome){
		$('.decka button').text('Click here');
	}
	function escapeHtml(str) {
		var div = document.createElement('div');
		div.appendChild(document.createTextNode(str));
		return div.innerHTML;
	};

	function getParameterByName(name) {
		var match = RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
		return match && decodeURIComponent(match[1].replace(/\+/g, ' '));
	}
	
	$('.deckname').html(escapeHtml(getParameterByName('name')));
	
	$.get('decks/'+getParameterByName('deck'), function(data, status){
		var deck = JSON.stringify(data);
		console.log(deck);
		$('#deckjson').html(deck);

		$('.decka').attr('href', escapeHtml('decks/'+getParameterByName('deck')));
		$('.decka').attr('download', escapeHtml(getParameterByName('name')+'.json'));
	});
});
