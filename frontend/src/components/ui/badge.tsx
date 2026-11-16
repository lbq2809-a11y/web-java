import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default: 'border-[#CCFBF1] bg-[#F0FDFA] text-[#0F766E]',
        secondary: 'border-[#E2E8F0] bg-[#F8FAFC] text-[#475569]',
        warning: 'border-[#F59E0B] bg-[#FEF3C7] text-[#B45309]',
        info: 'border-sky-200 bg-sky-50 text-sky-700',
        danger: 'border-red-200 bg-red-50 text-red-700',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge, badgeVariants };
