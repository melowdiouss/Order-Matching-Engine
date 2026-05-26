const form = document.getElementById('order-form');
const typeInput = document.getElementById('type');
const sideGroup = document.getElementById('side-group');
const priceGroup = document.getElementById('price-group');
const quantityGroup = document.getElementById('quantity-group');
const resultNode = document.getElementById('submit-result');
const bookOutput = document.getElementById('book-output');
const eventsList = document.getElementById('events-list');

const refreshBookBtn = document.getElementById('refresh-book');
const refreshEventsBtn = document.getElementById('refresh-events');

function syncFieldVisibility() {
    const type = typeInput.value;
    const isCancel = type === 'CANCEL';
    const isMarket = type === 'MARKET';

    sideGroup.style.display = isCancel ? 'none' : 'grid';
    priceGroup.style.display = isCancel || isMarket ? 'none' : 'grid';
    quantityGroup.style.display = isCancel ? 'none' : 'grid';
}

function setResult(message, ok) {
    resultNode.textContent = message;
    resultNode.className = ok ? 'status-line ok' : 'status-line err';
}

async function fetchBook() {
    const res = await fetch('/api/book?depth=10');
    if (!res.ok) {
        throw new Error('Failed to load order book');
    }
    const data = await res.json();
    bookOutput.textContent = data.snapshot || 'No data.';
}

async function fetchEvents() {
    const res = await fetch('/api/events?limit=30');
    if (!res.ok) {
        throw new Error('Failed to load events');
    }
    const data = await res.json();
    eventsList.innerHTML = '';

    const events = data.events || [];
    if (events.length === 0) {
        const li = document.createElement('li');
        li.textContent = 'No events yet.';
        eventsList.appendChild(li);
        return;
    }

    for (const event of events) {
        const li = document.createElement('li');
        li.textContent = event;
        eventsList.appendChild(li);
    }
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const payload = {
        type: document.getElementById('type').value,
        orderId: document.getElementById('orderId').value,
        userId: document.getElementById('userId').value,
        side: document.getElementById('side').value,
        price: Number(document.getElementById('price').value),
        quantity: Number(document.getElementById('quantity').value)
    };

    if (payload.type === 'CANCEL') {
        delete payload.side;
        delete payload.price;
        delete payload.quantity;
    } else if (payload.type === 'MARKET') {
        delete payload.price;
    }

    try {
        const res = await fetch('/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const body = await res.json();
        if (!res.ok || !body.accepted) {
            throw new Error(body.error || 'Order rejected');
        }

        setResult(`Accepted ${body.type} order ${body.orderId}`, true);
        await Promise.all([fetchBook(), fetchEvents()]);
    } catch (error) {
        setResult(error.message, false);
    }
});

refreshBookBtn.addEventListener('click', () => {
    fetchBook().catch((error) => setResult(error.message, false));
});

refreshEventsBtn.addEventListener('click', () => {
    fetchEvents().catch((error) => setResult(error.message, false));
});

typeInput.addEventListener('change', syncFieldVisibility);

syncFieldVisibility();
Promise.all([fetchBook(), fetchEvents()]).catch((error) => setResult(error.message, false));
setInterval(() => {
    Promise.all([fetchBook(), fetchEvents()]).catch(() => {});
}, 1500);
