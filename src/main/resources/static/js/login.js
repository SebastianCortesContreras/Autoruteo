const urlParams = new URLSearchParams(window.location.search);
const errorMessage = document.getElementById('error-msg');

if (urlParams.has('error')) {
    errorMessage.classList.add('is-visible');
}
