import React, { useRef } from 'react';
import { gsap, useGSAP, prefersReducedMotion } from './motion';

/** Number that counts from its previous value to the new one. */
export default function CountUp({ value = 0, duration = 0.9 }) {
  const el = useRef(null);
  const shown = useRef({ v: 0 });

  useGSAP(
    () => {
      const target = Number(value) || 0;
      if (prefersReducedMotion()) {
        shown.current.v = target;
        if (el.current) el.current.textContent = String(target);
        return;
      }
      gsap.to(shown.current, {
        v: target,
        duration,
        ease: 'power2.out',
        overwrite: true,
        onUpdate: () => {
          if (el.current) el.current.textContent = String(Math.round(shown.current.v));
        }
      });
    },
    { dependencies: [value], revertOnUpdate: false }
  );

  return <span ref={el}>0</span>;
}
