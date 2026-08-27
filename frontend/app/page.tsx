import { Nav } from "@/components/landing/Nav";
import { Hero } from "@/components/landing/Hero";
import { HowItWorks } from "@/components/landing/HowItWorks";
import { DifficultyBento } from "@/components/landing/DifficultyBento";
import { CtaBand } from "@/components/landing/CtaBand";
import { Footer } from "@/components/landing/Footer";

export default function Home() {
  return (
    <>
      <Nav />
      <a
        href="#main-content"
        className="sr-only z-50 rounded-md bg-accent px-4 py-2 font-medium text-accent-ink focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <main id="main-content" className="flex-1">
        <Hero />
        <HowItWorks />
        <DifficultyBento />
        <CtaBand />
      </main>
      <Footer />
    </>
  );
}
