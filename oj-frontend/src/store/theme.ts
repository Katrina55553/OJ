// src/store/theme.ts
import { defineStore } from "pinia";
import { ref, watch } from "vue";

export const useThemeStore = defineStore("theme", () => {
  const isDark = ref<boolean>(
    localStorage.getItem("theme") === "dark" ||
      (!localStorage.getItem("theme") &&
        window.matchMedia("(prefers-color-scheme: dark)").matches)
  );

  const toggleDark = () => {
    isDark.value = !isDark.value;
    localStorage.setItem("theme", isDark.value ? "dark" : "light");
    updateBodyClass();
  };

  const setDark = (value: boolean) => {
    isDark.value = value;
    localStorage.setItem("theme", value ? "dark" : "light");
    updateBodyClass();
  };

  const updateBodyClass = () => {
    if (isDark.value) {
      document.body.setAttribute("arco-theme", "dark");
    } else {
      document.body.removeAttribute("arco-theme");
    }
  };

  const mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  mediaQuery.addEventListener("change", (e) => {
    if (!localStorage.getItem("theme")) {
      isDark.value = e.matches;
      updateBodyClass();
    }
  });

  updateBodyClass();

  return { isDark, toggleDark, setDark };
});
