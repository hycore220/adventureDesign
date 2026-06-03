import * as React from "react";
import { cn } from "../../lib/utils";

export const Input = React.forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement>
>(({ className, type, ...props }, ref) => (
  <input
    ref={ref}
    type={type}
    className={cn(
      "flex h-9 w-full rounded-md border border-input bg-card px-3 py-1 text-sm tracking-tight transition-colors",
      "placeholder:text-muted-foreground/65 placeholder:tracking-normal",
      "focus-visible:outline-none focus-visible:border-primary/55 focus-visible:ring-2 focus-visible:ring-primary/12",
      "disabled:cursor-not-allowed disabled:opacity-50",
      className
    )}
    {...props}
  />
));
Input.displayName = "Input";
