const params = new URLSearchParams(window.location.search);
const errorMessage = new URLSearchParams(window.location.hash.slice(1)).get('message');
if (errorMessage && document.querySelector('.error')) {
    document.getElementById('description').textContent = errorMessage;
}
if (params.get('status') === 'cancel' && document.querySelector('.error')) {
    document.title = 'Checkout cancelled | ShoppingCart';
    document.getElementById('title').textContent = 'Checkout cancelled';
    document.getElementById('description').textContent = 'You left checkout before completing your purchase. Return to your cart whenever you are ready.';
}
const link = document.querySelector('main a');
const backUrl = new URL(link.getAttribute('href'), window.location.href);
for (const key of ['_ijt', '_ij_reload']) {
    if (params.has(key)) backUrl.searchParams.set(key, params.get(key));
}
link.href = backUrl.href;
