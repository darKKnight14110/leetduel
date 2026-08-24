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
      <main className="flex-1">
        <Hero />
        <HowItWorks />
        <DifficultyBento />
        <CtaBand />
      </main>
      <Footer />
    </>
  );
}
