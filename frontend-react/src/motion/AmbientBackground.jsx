import React, { useRef } from 'react';
import { gsap, useGSAP, prefersReducedMotion } from './motion';

/** Slow-drifting "sunlight" glows behind the page. Pointer-transparent and purely decorative. */
export default function AmbientBackground() {
  const root = useRef(null);

  useGSAP(
    () => {
      if (prefersReducedMotion()) return;
      gsap.utils.toArray('[data-orb]').forEach((orb, i) => {
        gsap.to(orb, {
          x: gsap.utils.random(-120, 120),
          y: gsap.utils.random(-80, 80),
          scale: gsap.utils.random(0.85, 1.2),
          duration: gsap.utils.random(14, 22),
          ease: 'sine.inOut',
          repeat: -1,
          yoyo: true,
          delay: i * 1.5
        });
      });
    },
    { scope: root }
  );

  return (
    <div ref={root} aria-hidden="true" className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
      <div data-orb className="absolute -top-40 -left-32 w-[520px] h-[520px] rounded-full bg-[#E5A93C]/[0.07] blur-3xl" />
      <div data-orb className="absolute top-1/3 -right-40 w-[460px] h-[460px] rounded-full bg-amber-600/[0.06] blur-3xl" />
      <div data-orb className="absolute -bottom-48 left-1/3 w-[480px] h-[480px] rounded-full bg-emerald-500/[0.04] blur-3xl" />
    </div>
  );
}
