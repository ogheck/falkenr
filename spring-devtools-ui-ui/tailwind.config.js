/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        shell: "#07111f",
        panel: "#0d1a2b",
        line: "rgba(157, 191, 255, 0.18)",
        accent: "#6be3ff",
        ember: "#ff7b72",
        mist: "#d8e8ff",
        gold: "#f3c969",
      },
      boxShadow: {
        halo: "0 24px 80px rgba(11, 43, 84, 0.4)",
      },
      backgroundImage: {
        grid: "linear-gradient(rgba(132, 157, 191, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(132, 157, 191, 0.08) 1px, transparent 1px)",
      },
    },
  },
  plugins: [],
};
