// Shared GSAP setup: one place for easing, timing and the reduced-motion switch.
import gsap from 'gsap';
import { useGSAP } from '@gsap/react';

gsap.registerPlugin(useGSAP);

gsap.defaults({ ease: 'power3.out', duration: 0.5 });

/** True when the user asked the OS for less motion: every effect below becomes a no-op. */
export const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Slide-and-fade entrance for a group of elements. clearProps hands styling back to Tailwind afterwards. */
export function revealIn(targets, opts = {}) {
  if (prefersReducedMotion() || !targets || (targets.length !== undefined && targets.length === 0)) return null;
  return gsap.from(targets, {
    y: 18,
    autoAlpha: 0,
    duration: 0.55,
    stagger: 0.06,
    clearProps: 'transform,opacity,visibility',
    ...opts
  });
}

export { gsap, useGSAP };
