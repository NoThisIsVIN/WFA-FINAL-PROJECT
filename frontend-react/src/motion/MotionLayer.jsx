import { useEffect } from 'react';
import { gsap, prefersReducedMotion } from './motion';

/**
 * App-wide motion that needs no per-component wiring:
 *  - modals (".fixed.inset-0" overlays): backdrop fade + panel spring-in
 *  - toasts ([data-toast]): slide in from the right
 *  - rows/cards added to an existing list (tbody > tr, [data-stagger]): soft stagger
 *  - buttons: press-down / spring-back feedback
 * A MutationObserver only sees nodes React actually inserts, so re-renders of polled data
 * (same keys, same DOM nodes) do not replay animations.
 */
export default function MotionLayer() {
  useEffect(() => {
    if (prefersReducedMotion()) return undefined;

    let pendingRows = [];
    let rowFlush = 0;

    const animateModal = (overlay) => {
      const panel = overlay.firstElementChild;
      const tl = gsap.timeline();
      tl.from(overlay, { autoAlpha: 0, duration: 0.25, ease: 'power1.out', clearProps: 'opacity,visibility' });
      if (panel) {
        tl.from(panel, { y: 28, scale: 0.94, autoAlpha: 0, duration: 0.5, ease: 'back.out(1.6)', clearProps: 'transform,opacity,visibility' }, '<0.05');
        tl.from(panel.children, { y: 10, autoAlpha: 0, duration: 0.35, stagger: 0.05, clearProps: 'transform,opacity,visibility' }, '<0.15');
      }
    };

    const queueRow = (el) => {
      pendingRows.push(el);
      if (!rowFlush) {
        rowFlush = requestAnimationFrame(() => {
          const rows = pendingRows.filter((r) => r.isConnected);
          pendingRows = [];
          rowFlush = 0;
          if (rows.length) {
            gsap.from(rows, { y: 12, autoAlpha: 0, duration: 0.45, stagger: 0.04, clearProps: 'transform,opacity,visibility' });
          }
        });
      }
    };

    const onAdded = (node) => {
      if (!(node instanceof HTMLElement)) return;
      if (node.matches('.fixed.inset-0:not([aria-hidden])')) return animateModal(node);
      if (node.matches('[data-toast]')) {
        return gsap.from(node, { x: 80, autoAlpha: 0, scale: 0.96, duration: 0.55, ease: 'back.out(1.4)', clearProps: 'transform,opacity,visibility' });
      }
      if (node.matches('tbody > tr, [data-stagger]')) return queueRow(node);
      // a whole new list/modal can arrive inside a bigger subtree
      const overlay = node.querySelector?.('.fixed.inset-0:not([aria-hidden])');
      if (overlay) animateModal(overlay);
      node.querySelectorAll?.('[data-toast]').forEach(onAdded);
    };

    const observer = new MutationObserver((mutations) => {
      for (const m of mutations) m.addedNodes.forEach(onAdded);
    });
    observer.observe(document.getElementById('root') || document.body, { childList: true, subtree: true });

    // Button press feedback (event delegation, so it covers every button in the app)
    const pressable = (t) => t?.closest?.('button:not(:disabled), [role="button"]');
    const down = (e) => {
      const b = pressable(e.target);
      if (b) gsap.to(b, { scale: 0.95, duration: 0.12, ease: 'power2.out', overwrite: 'auto' });
    };
    const up = (e) => {
      const b = pressable(e.target);
      if (b) gsap.to(b, { scale: 1, duration: 0.5, ease: 'elastic.out(1, 0.45)', overwrite: 'auto', clearProps: 'transform' });
    };
    document.addEventListener('pointerdown', down);
    document.addEventListener('pointerup', up);
    document.addEventListener('pointercancel', up);
    const out = (e) => {
      if (!e.relatedTarget || !pressable(e.target)?.contains(e.relatedTarget)) up(e);
    };
    document.addEventListener('pointerout', out);

    return () => {
      observer.disconnect();
      document.removeEventListener('pointerdown', down);
      document.removeEventListener('pointerup', up);
      document.removeEventListener('pointercancel', up);
      document.removeEventListener('pointerout', out);
      if (rowFlush) cancelAnimationFrame(rowFlush);
    };
  }, []);

  return null;
}
