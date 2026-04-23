/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#10131c",
        paper: "#f6f0e6",
        ember: "#b4512f",
        pine: "#1c3b34",
        sand: "#d6c6ae",
        slate: "#56616e",
        fog: "#ece3d5",
      },
      fontFamily: {
        display: ['"Iowan Old Style"', '"Palatino Linotype"', "serif"],
        body: ['"Avenir Next"', '"Segoe UI"', "sans-serif"],
      },
      boxShadow: {
        plate: "0 30px 80px rgba(16, 19, 28, 0.14)",
      },
      backgroundImage: {
        grain: "radial-gradient(circle at 1px 1px, rgba(16,19,28,0.08) 1px, transparent 0)",
      },
    },
  },
  plugins: [],
};
