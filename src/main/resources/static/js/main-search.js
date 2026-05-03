document.addEventListener("DOMContentLoaded", function () {
  const DEFAULT_LOCATION = "BCN Airport T1";

  const pickupInput = document.getElementById("pickupLocation");
  const dropoffInput = document.getElementById("dropoffLocation");

  const pickupDateText = document.getElementById("pickupDateText");
  const dropoffDateText = document.getElementById("dropoffDateText");

  const pickupHourInput = document.getElementById("pickupHour");
  const dropoffHourInput = document.getElementById("dropoffHour");

  if (pickupInput && !pickupInput.value.trim()) pickupInput.value = DEFAULT_LOCATION;
  if (dropoffInput && !dropoffInput.value.trim()) dropoffInput.value = DEFAULT_LOCATION;

  function formatIso(date) {
    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const year = date.getFullYear();
    return `${year}-${month}-${day}`;
  }

  function formatDisplay(date) {
    return date.toLocaleDateString("en-GB", {
      weekday: "short",
      day: "numeric",
      month: "short"
    });
  }

  const today = new Date();
  const tomorrow = new Date();
  tomorrow.setDate(today.getDate() + 1);

  let pickupDate = formatIso(today);
  let dropoffDate = formatIso(tomorrow);

  if (pickupDateText) {
    pickupDateText.innerText = formatDisplay(today);
    pickupDateText.setAttribute("data-date", pickupDate);
  }

  if (dropoffDateText) {
    dropoffDateText.innerText = formatDisplay(tomorrow);
    dropoffDateText.setAttribute("data-date", dropoffDate);
  }

  setTimeout(() => {
    initSingleDatePicker("pickupDateText", (isoDate) => {
      pickupDate = isoDate;
    });

    initSingleDatePicker("dropoffDateText", (isoDate) => {
      dropoffDate = isoDate;
    });
  }, 300);

  function initSingleDatePicker(textElementId, onSelect) {
    const textElement = document.getElementById(textElementId);
    if (!textElement) return;

    const calendarWrapper = textElement.closest(".js-calendar-el");
    if (!calendarWrapper) return;

    const cells = calendarWrapper.querySelectorAll(".elCalendar__sell:not(.-dark)");

    cells.forEach((cell) => {
      cell.addEventListener("click", function (event) {
        event.stopPropagation();

        calendarWrapper.querySelectorAll(".-is-active, .-is-in-path").forEach((el) => {
          el.classList.remove("-is-active", "-is-in-path");
        });

        cell.classList.add("-is-active");

        const isoDate = buildIsoDate(cell);
        textElement.innerText = buildDisplayDate(cell);
        textElement.setAttribute("data-date", isoDate);

        onSelect(isoDate);

        const popup = calendarWrapper.querySelector(".searchMenu-date__field");
        popup?.classList.remove("-is-active");
        calendarWrapper.classList.remove("-is-dd-wrap-active");
      });
    });
  }

  function buildIsoDate(cell) {
    const monthMap = {
      jan: "01", feb: "02", mar: "03", apr: "04",
      may: "05", jun: "06", jul: "07", aug: "08",
      sep: "09", oct: "10", nov: "11", dec: "12"
    };

    const day = cell.querySelector(".js-date").innerText.trim().padStart(2, "0");
    const month = monthMap[cell.getAttribute("data-month").toLowerCase()];

    const monthNumber = Number(month);
    const currentMonth = new Date().getMonth() + 1;
    let year = new Date().getFullYear();

    if (monthNumber < currentMonth) {
      year += 1;
    }

    return `${year}-${month}-${day}`;
  }

  function buildDisplayDate(cell) {
    const week = cell.getAttribute("data-week");
    const day = cell.querySelector(".js-date").innerText.trim();
    const month = cell.getAttribute("data-month");
    return `${week} ${day} ${month}`;
  }

  const searchButton = document.getElementById("searchCarsButton");
  if (!searchButton) return;

  searchButton.addEventListener("click", function () {
    const pickupLocation = pickupInput?.value.trim() || DEFAULT_LOCATION;
    const dropoffLocation = dropoffInput?.value.trim() || DEFAULT_LOCATION;

    const selectedPickupDate = pickupDateText?.getAttribute("data-date") || pickupDate;
    const selectedDropoffDate = dropoffDateText?.getAttribute("data-date") || dropoffDate;

    const pickupHour = pickupHourInput?.value || "10:00";
    const dropoffHour = dropoffHourInput?.value || "10:00";

    const params = new URLSearchParams({
      pickupLocation,
      dropoffLocation,
      pickupDateTime: `${selectedPickupDate}T${pickupHour}`,
      dropoffDateTime: `${selectedDropoffDate}T${dropoffHour}`
    });

    window.location.href = "/cars.html?" + params.toString();
  });
});